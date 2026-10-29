package com.learnium.generation;

import static com.learnium.generation.DraftQuestionFactory.ANSWER_KEY_INVALID;
import static com.learnium.generation.DraftQuestionFactory.DUPLICATE_STEM;
import static com.learnium.generation.DraftQuestionFactory.EXCEEDS_REQUESTED;
import static com.learnium.generation.DraftQuestionFactory.FIELD_TOO_LONG;
import static com.learnium.generation.DraftQuestionFactory.RUBRIC_INVALID;
import static com.learnium.generation.DraftQuestionFactory.SCHEMA_INVALID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.DraftQuestionFactory.DraftQuestion;
import com.learnium.generation.DraftQuestionFactory.ValidationOutcome;
import com.learnium.generation.GenerationResultCounts.TypeResult;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The validation contract, type by type: every rule drops with its
 * reason code, ids are server-assigned, and received always equals
 * accepted + dropped. Inputs are raw JSON exactly as a model returns it.
 */
class DraftQuestionFactoryTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final DraftQuestionFactory factory = new DraftQuestionFactory();

    private static JsonNode items(String json) throws Exception {
        return JSON.readTree(json);
    }

    private static Map<GeneratedQuestionType, Integer> requested(Object... typeThenCount) {
        EnumMap<GeneratedQuestionType, Integer> requested = new EnumMap<>(GeneratedQuestionType.class);
        for (int i = 0; i < typeThenCount.length; i += 2) {
            requested.put((GeneratedQuestionType) typeThenCount[i], (Integer) typeThenCount[i + 1]);
        }
        return requested;
    }

    private static TypeResult result(ValidationOutcome outcome, GeneratedQuestionType type) {
        return outcome.counts().forType(type);
    }

    private static void assertDropped(
            ValidationOutcome outcome, GeneratedQuestionType type, String reason) {
        TypeResult result = result(outcome, type);
        assertThat(result.accepted()).isZero();
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(result.dropReasons()).containsEntry(reason, 1);
        assertThat(outcome.drafts()).isEmpty();
    }

    private static final String MCQ =
            "{\"type\":\"MCQ\",\"stem\":\"What is 1/2 + 1/4?\",\"options\":["
                    + "{\"text\":\"3/4\"},{\"text\":\"1/2\"},{\"text\":\"2/6\"},{\"text\":\"1/6\"}],"
                    + "\"correctIndex\":0}";

    @Test
    void validMcqGetsServerAssignedOptionIdsAndTheCorrectRef() throws Exception {
        ValidationOutcome outcome =
                factory.validate(items("[" + MCQ + "]"), requested(GeneratedQuestionType.MCQ, 1));

        assertThat(outcome.drafts()).hasSize(1);
        DraftQuestion draft = outcome.drafts().get(0);
        assertThat(draft.type()).isEqualTo(GeneratedQuestionType.MCQ);
        assertThat(draft.correctRef()).isEqualTo("a");
        JsonNode options = draft.payload().get("options");
        assertThat(options).hasSize(4);
        assertThat(options.get(0).get("id").asText()).isEqualTo("a");
        assertThat(options.get(3).get("id").asText()).isEqualTo("d");
        TypeResult result = result(outcome, GeneratedQuestionType.MCQ);
        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.received()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.dropped()).isZero();
    }

    @Test
    void mcqCorrectIndexSelectsTheOptionId() throws Exception {
        String item = MCQ.replace("\"correctIndex\":0", "\"correctIndex\":2");
        ValidationOutcome outcome =
                factory.validate(items("[" + item + "]"), requested(GeneratedQuestionType.MCQ, 1));
        assertThat(outcome.drafts().get(0).correctRef()).isEqualTo("c");
    }

    @Test
    void mcqShapeViolationsDropAsSchemaInvalid() throws Exception {
        String threeOptions =
                "{\"type\":\"MCQ\",\"stem\":\"S?\",\"options\":[{\"text\":\"a\"},{\"text\":\"b\"},"
                        + "{\"text\":\"c\"}],\"correctIndex\":0}";
        assertDropped(
                factory.validate(items("[" + threeOptions + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        String blankOption = MCQ.replace("\"1/2\"", "\"  \"");
        assertDropped(
                factory.validate(items("[" + blankOption + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        String duplicateOptions = MCQ.replace("\"2/6\"", "\" 1/2 \"");
        assertDropped(
                factory.validate(
                        items("[" + duplicateOptions + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        String blankStem = MCQ.replace("What is 1/2 + 1/4?", " ");
        assertDropped(
                factory.validate(items("[" + blankStem + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        String noOptions = "{\"type\":\"MCQ\",\"stem\":\"S?\",\"correctIndex\":0}";
        assertDropped(
                factory.validate(items("[" + noOptions + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
    }

    @Test
    void mcqBadAnswerKeysDropAsAnswerKeyInvalid() throws Exception {
        String outOfRange = MCQ.replace("\"correctIndex\":0", "\"correctIndex\":4");
        assertDropped(
                factory.validate(items("[" + outOfRange + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                ANSWER_KEY_INVALID);
        String missing = MCQ.replace(",\"correctIndex\":0", "");
        assertDropped(
                factory.validate(items("[" + missing + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                ANSWER_KEY_INVALID);
        String fractional = MCQ.replace("\"correctIndex\":0", "\"correctIndex\":1.5");
        assertDropped(
                factory.validate(items("[" + fractional + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                ANSWER_KEY_INVALID);
    }

    @Test
    void overLongFieldsDropAsFieldTooLong() throws Exception {
        String longStem = MCQ.replace("What is 1/2 + 1/4?", "x".repeat(501));
        assertDropped(
                factory.validate(items("[" + longStem + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                FIELD_TOO_LONG);
        String longOption = MCQ.replace("\"3/4\"", "\"" + "y".repeat(201) + "\"");
        assertDropped(
                factory.validate(items("[" + longOption + "]"), requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                FIELD_TOO_LONG);
    }

    private static final String FIB =
            "{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\"One half equals _____ quarters.\","
                    + "\"answer\":\"two\"}";

    @Test
    void validFillBlankKeepsTheAnswerAsItsRef() throws Exception {
        ValidationOutcome outcome =
                factory.validate(items("[" + FIB + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1));
        assertThat(outcome.drafts()).hasSize(1);
        assertThat(outcome.drafts().get(0).correctRef()).isEqualTo("two");
        assertThat(outcome.drafts().get(0).payload().get("stemWithBlank").asText())
                .contains("_____");
    }

    @Test
    void fillBlankFallsBackToTheStemField() throws Exception {
        // No stemWithBlank at all: the text rides in the generic stem field.
        String stemOnly =
                "{\"type\":\"FILL_BLANK\",\"stem\":\"One half equals _____ quarters.\","
                        + "\"answer\":\"two\"}";
        ValidationOutcome outcome =
                factory.validate(
                        items("[" + stemOnly + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1));
        assertThat(outcome.drafts()).hasSize(1);
        assertThat(outcome.drafts().get(0).correctRef()).isEqualTo("two");
        assertThat(outcome.drafts().get(0).payload().get("stemWithBlank").asText())
                .isEqualTo("One half equals _____ quarters.");

        // A blank stemWithBlank falls back the same way.
        String blankCanonicalField =
                "{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\" \","
                        + "\"stem\":\"One half equals _____ quarters.\",\"answer\":\"two\"}";
        ValidationOutcome blankOutcome =
                factory.validate(
                        items("[" + blankCanonicalField + "]"),
                        requested(GeneratedQuestionType.FILL_BLANK, 1));
        assertThat(blankOutcome.drafts()).hasSize(1);
        assertThat(blankOutcome.drafts().get(0).payload().get("stemWithBlank").asText())
                .isEqualTo("One half equals _____ quarters.");
    }

    @Test
    void fillBlankCollapsesLongUnderscoreRunsIntoOneMarker() throws Exception {
        String longRun =
                "{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\"One half equals "
                        + "_".repeat(21)
                        + " quarters.\",\"answer\":\"two\"}";
        ValidationOutcome outcome =
                factory.validate(
                        items("[" + longRun + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1));
        assertThat(outcome.drafts()).hasSize(1);
        // The persisted payload carries the normalized single marker.
        assertThat(outcome.drafts().get(0).payload().get("stemWithBlank").asText())
                .isEqualTo("One half equals _____ quarters.");
    }

    @Test
    void fillBlankNormalizationLeavesRealDefectsDropped() throws Exception {
        // One real marker plus a long run: two blanks once the run
        // collapses, so still not exactly one.
        String longRunPlusMarker =
                "{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\"One _____ half equals "
                        + "_".repeat(21)
                        + " quarters.\",\"answer\":\"two\"}";
        assertDropped(
                factory.validate(
                        items("[" + longRunPlusMarker + "]"),
                        requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
        // Four underscores are below marker length: no marker at all.
        String shortRun =
                "{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\"One half equals ____ quarters.\","
                        + "\"answer\":\"two\"}";
        assertDropped(
                factory.validate(
                        items("[" + shortRun + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
        // Neither text field present: nothing to fall back to.
        assertDropped(
                factory.validate(
                        items("[{\"type\":\"FILL_BLANK\",\"answer\":\"two\"}]"),
                        requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
    }

    @Test
    void fillBlankNeedsExactlyOneMarkerAndAShortAnswer() throws Exception {
        String noMarker = FIB.replace(" _____", "");
        assertDropped(
                factory.validate(items("[" + noMarker + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
        String twoMarkers = FIB.replace("quarters.", "_____ quarters.");
        assertDropped(
                factory.validate(
                        items("[" + twoMarkers + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
        String blankAnswer = FIB.replace("\"two\"", "\" \"");
        assertDropped(
                factory.validate(
                        items("[" + blankAnswer + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
        String longAnswer = FIB.replace("\"two\"", "\"" + "a".repeat(51) + "\"");
        assertDropped(
                factory.validate(
                        items("[" + longAnswer + "]"), requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                FIELD_TOO_LONG);
    }

    @Test
    void validFlashcardAndItsLimits() throws Exception {
        String card = "{\"type\":\"FLASHCARD\",\"front\":\"One half\",\"back\":\"Two quarters\"}";
        ValidationOutcome outcome =
                factory.validate(items("[" + card + "]"), requested(GeneratedQuestionType.FLASHCARD, 1));
        assertThat(outcome.drafts()).hasSize(1);
        assertThat(outcome.drafts().get(0).correctRef()).isEqualTo("back");

        String longBack =
                "{\"type\":\"FLASHCARD\",\"front\":\"One half\",\"back\":\"" + "b".repeat(2001) + "\"}";
        assertDropped(
                factory.validate(
                        items("[" + longBack + "]"), requested(GeneratedQuestionType.FLASHCARD, 1)),
                GeneratedQuestionType.FLASHCARD,
                FIELD_TOO_LONG);
        String blankFront = "{\"type\":\"FLASHCARD\",\"front\":\"\",\"back\":\"Two quarters\"}";
        assertDropped(
                factory.validate(
                        items("[" + blankFront + "]"), requested(GeneratedQuestionType.FLASHCARD, 1)),
                GeneratedQuestionType.FLASHCARD,
                SCHEMA_INVALID);
    }

    private static final String NUMERIC =
            "{\"type\":\"NUMERIC\",\"stem\":\"What is 3/4 of 20?\",\"answerValue\":15,"
                    + "\"tolerance\":0.1,\"unit\":\"\",\"workedSolution\":\"20 x 3/4 = 15\"}";

    @Test
    void validNumericBuildsACompactJsonRef() throws Exception {
        ValidationOutcome outcome =
                factory.validate(items("[" + NUMERIC + "]"), requested(GeneratedQuestionType.NUMERIC, 1));
        assertThat(outcome.drafts()).hasSize(1);
        DraftQuestion draft = outcome.drafts().get(0);
        JsonNode ref = JSON.readTree(draft.correctRef());
        assertThat(ref.get("value").asDouble()).isEqualTo(15.0);
        assertThat(ref.get("tolerance").asDouble()).isEqualTo(0.1);
        assertThat(ref.get("unit").asText()).isEmpty();
        assertThat(draft.payload().get("workedSolution").asText()).isEqualTo("20 x 3/4 = 15");
    }

    @Test
    void numericDefaultsToleranceAndUnit() throws Exception {
        String minimal =
                "{\"type\":\"NUMERIC\",\"stem\":\"2 + 2?\",\"answerValue\":4,"
                        + "\"workedSolution\":\"2 + 2 = 4\"}";
        ValidationOutcome outcome =
                factory.validate(items("[" + minimal + "]"), requested(GeneratedQuestionType.NUMERIC, 1));
        JsonNode ref = JSON.readTree(outcome.drafts().get(0).correctRef());
        assertThat(ref.get("tolerance").asDouble()).isZero();
        assertThat(ref.get("unit").asText()).isEmpty();
    }

    @Test
    void numericViolations() throws Exception {
        String noValue = NUMERIC.replace("\"answerValue\":15,", "");
        assertDropped(
                factory.validate(items("[" + noValue + "]"), requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        String negativeTolerance = NUMERIC.replace("\"tolerance\":0.1", "\"tolerance\":-1");
        assertDropped(
                factory.validate(
                        items("[" + negativeTolerance + "]"), requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        String blankSolution = NUMERIC.replace("20 x 3/4 = 15", " ");
        assertDropped(
                factory.validate(
                        items("[" + blankSolution + "]"), requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        String longUnit = NUMERIC.replace("\"unit\":\"\"", "\"unit\":\"" + "u".repeat(21) + "\"");
        assertDropped(
                factory.validate(items("[" + longUnit + "]"), requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                FIELD_TOO_LONG);
        // A ref that cannot fit the correct_ref column drops instead of truncating.
        String wideRef =
                "{\"type\":\"NUMERIC\",\"stem\":\"Big?\",\"answerValue\":123456789.123456789,"
                        + "\"tolerance\":0.000001,\"unit\":\"kilometres per hour\","
                        + "\"workedSolution\":\"steps\"}";
        assertDropped(
                factory.validate(items("[" + wideRef + "]"), requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                FIELD_TOO_LONG);
    }

    private static final String WRITTEN =
            "{\"type\":\"WRITTEN\",\"stem\":\"Explain why 1/2 equals 2/4.\","
                    + "\"modelAnswer\":\"Both name the same portion.\",\"rubric\":["
                    + "{\"criterion\":\"States equivalence\",\"maxScore\":2},"
                    + "{\"criterion\":\"Uses a diagram or example\",\"maxScore\":2},"
                    + "{\"criterion\":\"Explains numerator and denominator\",\"maxScore\":1}]}";

    @Test
    void validWrittenGetsStableCriterionIds() throws Exception {
        ValidationOutcome outcome =
                factory.validate(items("[" + WRITTEN + "]"), requested(GeneratedQuestionType.WRITTEN, 1));
        assertThat(outcome.drafts()).hasSize(1);
        DraftQuestion draft = outcome.drafts().get(0);
        assertThat(draft.correctRef()).isEqualTo("modelAnswer");
        JsonNode rubric = draft.payload().get("rubric");
        assertThat(rubric).hasSize(3);
        assertThat(rubric.get(0).get("id").asText()).isEqualTo("crit-1");
        assertThat(rubric.get(2).get("id").asText()).isEqualTo("crit-3");
        assertThat(rubric.get(0).get("maxScore").asDouble()).isEqualTo(2.0);
    }

    @Test
    void writtenRubricViolations() throws Exception {
        String twoCriteria =
                WRITTEN.replace(
                        ",{\"criterion\":\"Explains numerator and denominator\",\"maxScore\":1}", "");
        assertDropped(
                factory.validate(
                        items("[" + twoCriteria + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String sixCriteria =
                WRITTEN.replace(
                        "\"maxScore\":1}]}",
                        "\"maxScore\":1},{\"criterion\":\"a\",\"maxScore\":1},"
                                + "{\"criterion\":\"b\",\"maxScore\":1},{\"criterion\":\"c\",\"maxScore\":1}]}");
        assertDropped(
                factory.validate(
                        items("[" + sixCriteria + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String zeroScore = WRITTEN.replace("\"maxScore\":2", "\"maxScore\":0");
        assertDropped(
                factory.validate(items("[" + zeroScore + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String overScore = WRITTEN.replace("\"maxScore\":2", "\"maxScore\":101");
        assertDropped(
                factory.validate(items("[" + overScore + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String blankCriterion = WRITTEN.replace("States equivalence", " ");
        assertDropped(
                factory.validate(
                        items("[" + blankCriterion + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String missingScore = WRITTEN.replace(",\"maxScore\":2}", "}");
        assertDropped(
                factory.validate(
                        items("[" + missingScore + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        String longCriterion = WRITTEN.replace("States equivalence", "c".repeat(201));
        assertDropped(
                factory.validate(
                        items("[" + longCriterion + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                FIELD_TOO_LONG);
        String blankModelAnswer = WRITTEN.replace("Both name the same portion.", " ");
        assertDropped(
                factory.validate(
                        items("[" + blankModelAnswer + "]"), requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                SCHEMA_INVALID);
    }

    @Test
    void duplicateStemsDropAcrossTypesAfterNormalization() throws Exception {
        String sameStemWritten =
                "{\"type\":\"WRITTEN\",\"stem\":\"  what IS 1/2 + 1/4? \",\"modelAnswer\":\"3/4.\","
                        + "\"rubric\":[{\"criterion\":\"a\",\"maxScore\":1},"
                        + "{\"criterion\":\"b\",\"maxScore\":1},{\"criterion\":\"c\",\"maxScore\":1}]}";
        ValidationOutcome outcome =
                factory.validate(
                        items("[" + MCQ + "," + sameStemWritten + "]"),
                        requested(GeneratedQuestionType.MCQ, 1, GeneratedQuestionType.WRITTEN, 1));
        assertThat(outcome.drafts()).hasSize(1);
        TypeResult written = result(outcome, GeneratedQuestionType.WRITTEN);
        assertThat(written.dropped()).isEqualTo(1);
        assertThat(written.dropReasons()).containsEntry(DUPLICATE_STEM, 1);
    }

    @Test
    void overDeliveryDropsWithExceedsRequested() throws Exception {
        String second =
                MCQ.replace("What is 1/2 + 1/4?", "What is 1/3 + 1/3?")
                        .replace("\"correctIndex\":0", "\"correctIndex\":1");
        ValidationOutcome outcome =
                factory.validate(
                        items("[" + MCQ + "," + second + "]"), requested(GeneratedQuestionType.MCQ, 1));
        TypeResult result = result(outcome, GeneratedQuestionType.MCQ);
        assertThat(result.received()).isEqualTo(2);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(result.dropReasons()).containsEntry(EXCEEDS_REQUESTED, 1);
        // A type that was never requested accepts nothing.
        ValidationOutcome unrequested =
                factory.validate(items("[" + WRITTEN + "]"), requested(GeneratedQuestionType.MCQ, 1));
        assertThat(result(unrequested, GeneratedQuestionType.WRITTEN).dropReasons())
                .containsEntry(EXCEEDS_REQUESTED, 1);
    }

    @Test
    void unattributableItemsAreIgnored() throws Exception {
        ValidationOutcome outcome =
                factory.validate(
                        items("[{\"type\":\"ESSAY\",\"stem\":\"?\"},42,{\"stem\":\"no type\"}," + MCQ + "]"),
                        requested(GeneratedQuestionType.MCQ, 1));
        TypeResult result = result(outcome, GeneratedQuestionType.MCQ);
        assertThat(result.received()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(outcome.counts().totalAccepted()).isEqualTo(1);
    }

    @Test
    void nullItemsInTheArrayAreIgnoredEntirely() throws Exception {
        ValidationOutcome outcome =
                factory.validate(items("[null," + MCQ + "]"), requested(GeneratedQuestionType.MCQ, 1));

        TypeResult result = result(outcome, GeneratedQuestionType.MCQ);
        assertThat(result.received()).isEqualTo(1);
        assertThat(result.accepted()).isEqualTo(1);
    }

    @Test
    void mcqMissingPiecesDropAsSchemaInvalid() throws Exception {
        // No options at all.
        assertDropped(
                factory.validate(
                        items("[{\"type\":\"MCQ\",\"stem\":\"Q?\",\"correctIndex\":0}]"),
                        requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        // Options that are not an array.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"MCQ\",\"stem\":\"Q?\",\"options\":\"four\","
                                        + "\"correctIndex\":0}]"),
                        requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        // An option without text.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"MCQ\",\"stem\":\"Q?\",\"options\":[{},"
                                        + "{\"text\":\"b\"},{\"text\":\"c\"},{\"text\":\"d\"}],"
                                        + "\"correctIndex\":0}]"),
                        requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
        // No stem field at all.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"MCQ\",\"options\":[{\"text\":\"a\"},{\"text\":\"b\"},"
                                        + "{\"text\":\"c\"},{\"text\":\"d\"}],\"correctIndex\":0}]"),
                        requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                SCHEMA_INVALID);
    }

    @Test
    void mcqNegativeCorrectIndexDropsAsAnswerKeyInvalid() throws Exception {
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"MCQ\",\"stem\":\"Q?\",\"options\":[{\"text\":\"a\"},"
                                        + "{\"text\":\"b\"},{\"text\":\"c\"},{\"text\":\"d\"}],"
                                        + "\"correctIndex\":-1}]"),
                        requested(GeneratedQuestionType.MCQ, 1)),
                GeneratedQuestionType.MCQ,
                ANSWER_KEY_INVALID);
    }

    @Test
    void fillBlankOverLongStemDropsAsFieldTooLong() throws Exception {
        String stem = "_____ " + "x".repeat(500);
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\""
                                        + stem
                                        + "\",\"answer\":\"half\"}]"),
                        requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                FIELD_TOO_LONG);
    }

    @Test
    void fillBlankMissingAnswerDropsAsSchemaInvalid() throws Exception {
        assertDropped(
                factory.validate(
                        items("[{\"type\":\"FILL_BLANK\",\"stemWithBlank\":\"One _____ is half.\"}]"),
                        requested(GeneratedQuestionType.FILL_BLANK, 1)),
                GeneratedQuestionType.FILL_BLANK,
                SCHEMA_INVALID);
    }

    @Test
    void numericMissingStemAndNonFiniteNumbersDrop() throws Exception {
        // No stem at all.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"answerValue\":3.5,"
                                        + "\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        // answerValue beyond double range parses as infinity.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\","
                                        + "\"answerValue\":1e999,\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        // Same for an infinite tolerance.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\",\"answerValue\":3.5,"
                                        + "\"tolerance\":1e999,\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
    }

    @Test
    void writtenWithoutARubricDropsAsRubricInvalid() throws Exception {
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\"}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
    }

    @Test
    void writtenRubricShapeAndNonFiniteScoreDrop() throws Exception {
        // Rubric that is not an array.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\",\"rubric\":{}}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        // Six criteria exceeds the 3-5 band.
        String six =
                "\"rubric\":[{\"criterion\":\"A\",\"maxScore\":5},{\"criterion\":\"B\",\"maxScore\":5},"
                        + "{\"criterion\":\"C\",\"maxScore\":5},{\"criterion\":\"D\",\"maxScore\":5},"
                        + "{\"criterion\":\"E\",\"maxScore\":5},{\"criterion\":\"F\",\"maxScore\":5}]";
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\","
                                        + six
                                        + "}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        // A max score beyond double range parses as infinity.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\",\"rubric\":["
                                        + "{\"criterion\":\"Accuracy\",\"maxScore\":1e999},"
                                        + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                                        + "{\"criterion\":\"Clarity\",\"maxScore\":5}]}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
    }

    @Test
    void numericTextualValuesDropAndNonTextUnitDefaultsToEmpty() throws Exception {
        // answerValue as a string is not a number.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\","
                                        + "\"answerValue\":\"3.5\",\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        // tolerance as a string is not a number.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\",\"answerValue\":3.5,"
                                        + "\"tolerance\":\"wide\",\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        // Blank worked solution.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\",\"answerValue\":3.5,"
                                        + "\"workedSolution\":\"  \"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1)),
                GeneratedQuestionType.NUMERIC,
                SCHEMA_INVALID);
        // A non-text unit is ignored (defaults to empty), not a drop.
        ValidationOutcome outcome =
                factory.validate(
                        items(
                                "[{\"type\":\"NUMERIC\",\"stem\":\"Compute.\",\"answerValue\":3.5,"
                                        + "\"unit\":7,\"workedSolution\":\"Work.\"}]"),
                        requested(GeneratedQuestionType.NUMERIC, 1));
        assertThat(outcome.drafts()).hasSize(1);
        assertThat(outcome.drafts().get(0).payload().get("unit").asText()).isEmpty();
    }

    @Test
    void writtenMissingPiecesAndBadRubricEntriesDrop() throws Exception {
        String rubric =
                "\"rubric\":[{\"criterion\":\"Accuracy\",\"maxScore\":5},"
                        + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                        + "{\"criterion\":\"Clarity\",\"maxScore\":5}]";
        // Missing model answer.
        assertDropped(
                factory.validate(
                        items("[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\"," + rubric + "}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                SCHEMA_INVALID);
        // Over-long stem.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\""
                                        + "x".repeat(501)
                                        + "\",\"modelAnswer\":\"Answer.\","
                                        + rubric
                                        + "}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                FIELD_TOO_LONG);
        // A rubric entry that is not an object.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\",\"rubric\":[\"Accuracy\","
                                        + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                                        + "{\"criterion\":\"Clarity\",\"maxScore\":5}]}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        // A blank criterion.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\",\"rubric\":["
                                        + "{\"criterion\":\" \",\"maxScore\":5},"
                                        + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                                        + "{\"criterion\":\"Clarity\",\"maxScore\":5}]}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
        // A non-numeric max score.
        assertDropped(
                factory.validate(
                        items(
                                "[{\"type\":\"WRITTEN\",\"stem\":\"Explain.\","
                                        + "\"modelAnswer\":\"Answer.\",\"rubric\":["
                                        + "{\"criterion\":\"Accuracy\",\"maxScore\":\"five\"},"
                                        + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                                        + "{\"criterion\":\"Clarity\",\"maxScore\":5}]}]"),
                        requested(GeneratedQuestionType.WRITTEN, 1)),
                GeneratedQuestionType.WRITTEN,
                RUBRIC_INVALID);
    }

    @Test
    void emptyArrayYieldsZeroCounts() throws Exception {
        ValidationOutcome outcome =
                factory.validate(
                        items("[]"),
                        requested(GeneratedQuestionType.MCQ, 2, GeneratedQuestionType.WRITTEN, 1));
        assertThat(outcome.drafts()).isEmpty();
        assertThat(outcome.counts().totalAccepted()).isZero();
        assertThat(result(outcome, GeneratedQuestionType.MCQ).requested()).isEqualTo(2);
        assertThat(result(outcome, GeneratedQuestionType.NUMERIC).requested()).isZero();
    }
}
