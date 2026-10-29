package com.learnium.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationResultCounts.TypeResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side validation + materialization of raw model output — the
 * step that makes provider quality survivable. Every item is checked
 * against its type's rules; valid items become drafts with
 * server-assigned ids (MCQ option ids, rubric criterion ids) that stay
 * stable through later teacher edits; invalid items are dropped with a
 * counted reason code. Nothing here trusts the model: counts are capped
 * at what was requested, stems are deduplicated across the whole job,
 * and every persisted field is length-bounded (the fill-blank answer
 * additionally fits the correct_ref column it is stored in).
 */
public final class DraftQuestionFactory {

    public static final String SCHEMA_INVALID = "SCHEMA_INVALID";
    public static final String DUPLICATE_STEM = "DUPLICATE_STEM";
    public static final String ANSWER_KEY_INVALID = "ANSWER_KEY_INVALID";
    public static final String RUBRIC_INVALID = "RUBRIC_INVALID";
    public static final String FIELD_TOO_LONG = "FIELD_TOO_LONG";
    public static final String EXCEEDS_REQUESTED = "EXCEEDS_REQUESTED";

    static final int STEM_MAX = 500;
    static final int OPTION_MAX = 200;
    static final int FILL_BLANK_ANSWER_MAX = 50;
    static final int FLASHCARD_FRONT_MAX = 500;
    static final int FLASHCARD_BACK_MAX = 2000;
    static final int UNIT_MAX = 20;
    static final int WORKED_SOLUTION_MAX = 4000;
    static final int MODEL_ANSWER_MAX = 4000;
    static final int CRITERION_MAX = 200;
    static final int CORRECT_REF_MAX = 50;
    static final String BLANK_MARKER = "_____";
    static final String OPTION_IDS = "abcd";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One validated draft: type, display payload, and its grading reference. */
    public record DraftQuestion(GeneratedQuestionType type, JsonNode payload, String correctRef) {}

    /** Validation outcome: the surviving drafts plus the per-type tallies. */
    public record ValidationOutcome(GenerationResultCounts counts, List<DraftQuestion> drafts) {}

    private record Verdict(DraftQuestion draft, String stemKey, String dropReason) {
        static Verdict ok(DraftQuestion draft, String stemKey) {
            return new Verdict(draft, stemKey, null);
        }

        static Verdict drop(String reason) {
            return new Verdict(null, null, reason);
        }
    }

    private static final class Tally {
        private int received;
        private int accepted;
        private int dropped;
        private final Map<String, Integer> reasons = new LinkedHashMap<>();

        private void drop(String reason) {
            dropped++;
            reasons.merge(reason, 1, Integer::sum);
        }
    }

    public ValidationOutcome validate(
            JsonNode questions, Map<GeneratedQuestionType, Integer> requested) {
        EnumMap<GeneratedQuestionType, Tally> tallies = new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            tallies.put(type, new Tally());
        }
        List<DraftQuestion> drafts = new ArrayList<>();
        Set<String> acceptedStems = new HashSet<>();
        for (JsonNode item : questions) {
            GeneratedQuestionType type = typeOf(item);
            if (type == null) {
                // Unattributable (not an object, no usable type tag): it
                // cannot be counted against any type, so it is ignored —
                // the requested types simply receive less.
                continue;
            }
            Tally tally = tallies.get(type);
            tally.received++;
            if (tally.accepted >= requested.getOrDefault(type, 0)) {
                tally.drop(EXCEEDS_REQUESTED);
                continue;
            }
            Verdict verdict = validateItem(type, item);
            if (verdict.dropReason() != null) {
                tally.drop(verdict.dropReason());
                continue;
            }
            if (!acceptedStems.add(verdict.stemKey())) {
                tally.drop(DUPLICATE_STEM);
                continue;
            }
            tally.accepted++;
            drafts.add(verdict.draft());
        }
        EnumMap<GeneratedQuestionType, TypeResult> results = new EnumMap<>(GeneratedQuestionType.class);
        tallies.forEach(
                (type, tally) ->
                        results.put(
                                type,
                                new TypeResult(
                                        requested.getOrDefault(type, 0),
                                        tally.received,
                                        tally.accepted,
                                        tally.dropped,
                                        tally.reasons)));
        return new ValidationOutcome(new GenerationResultCounts(results), List.copyOf(drafts));
    }

    private static GeneratedQuestionType typeOf(JsonNode item) {
        // Items come straight from the parsed array, so item is never a
        // Java null (a JSON null literal arrives as a NullNode).
        if (!item.isObject()) {
            return null;
        }
        String type = JsonFields.textOrNull(item, "type");
        if (type == null) {
            return null;
        }
        try {
            return GeneratedQuestionType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Verdict validateItem(GeneratedQuestionType type, JsonNode item) {
        return switch (type) {
            case MCQ -> validateMcq(item);
            case FILL_BLANK -> validateFillBlank(item);
            case FLASHCARD -> validateFlashcard(item);
            case NUMERIC -> validateNumeric(item);
            case WRITTEN -> validateWritten(item);
        };
    }

    private Verdict validateMcq(JsonNode item) {
        String stemReason = textProblem(item, "stem", STEM_MAX);
        if (stemReason != null) {
            return Verdict.drop(stemReason);
        }
        JsonNode options = JsonFields.childOrNull(item, "options");
        if (options == null || !options.isArray() || options.size() != 4) {
            return Verdict.drop(SCHEMA_INVALID);
        }
        List<String> texts = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (JsonNode option : options) {
            String text = JsonFields.textOrNull(option, "text");
            if (text == null || text.isBlank()) {
                return Verdict.drop(SCHEMA_INVALID);
            }
            if (text.length() > OPTION_MAX) {
                return Verdict.drop(FIELD_TOO_LONG);
            }
            texts.add(text);
            distinct.add(normalize(text));
        }
        if (distinct.size() != texts.size()) {
            return Verdict.drop(SCHEMA_INVALID);
        }
        JsonNode correctIndex = JsonFields.childOrNull(item, "correctIndex");
        if (correctIndex == null
                || !correctIndex.isIntegralNumber()
                || correctIndex.asInt() < 0
                || correctIndex.asInt() > 3) {
            return Verdict.drop(ANSWER_KEY_INVALID);
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("stem", JsonFields.textOrNull(item, "stem"));
        ArrayNode payloadOptions = payload.putArray("options");
        for (int i = 0; i < texts.size(); i++) {
            ObjectNode option = payloadOptions.addObject();
            option.put("id", String.valueOf(OPTION_IDS.charAt(i)));
            option.put("text", texts.get(i));
        }
        String correctRef = String.valueOf(OPTION_IDS.charAt(correctIndex.asInt()));
        return Verdict.ok(new DraftQuestion(GeneratedQuestionType.MCQ, payload, correctRef),
                stemKey(JsonFields.textOrNull(item, "stem")));
    }

    private Verdict validateFillBlank(JsonNode item) {
        String stem = JsonFields.textOrNull(item, "stemWithBlank");
        String stemReason = textProblem(stem, STEM_MAX);
        if (stemReason != null) {
            return Verdict.drop(stemReason);
        }
        if (countOccurrences(stem, BLANK_MARKER) != 1) {
            return Verdict.drop(SCHEMA_INVALID);
        }
        String answer = JsonFields.textOrNull(item, "answer");
        if (answer == null || answer.isBlank()) {
            return Verdict.drop(SCHEMA_INVALID);
        }
        if (answer.length() > FILL_BLANK_ANSWER_MAX) {
            return Verdict.drop(FIELD_TOO_LONG);
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("stemWithBlank", stem);
        payload.put("answer", answer);
        return Verdict.ok(
                new DraftQuestion(GeneratedQuestionType.FILL_BLANK, payload, answer), stemKey(stem));
    }

    private Verdict validateFlashcard(JsonNode item) {
        String frontReason = textProblem(item, "front", FLASHCARD_FRONT_MAX);
        if (frontReason != null) {
            return Verdict.drop(frontReason);
        }
        String backReason = textProblem(item, "back", FLASHCARD_BACK_MAX);
        if (backReason != null) {
            return Verdict.drop(backReason);
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("front", JsonFields.textOrNull(item, "front"));
        payload.put("back", JsonFields.textOrNull(item, "back"));
        return Verdict.ok(
                new DraftQuestion(GeneratedQuestionType.FLASHCARD, payload, "back"),
                stemKey(JsonFields.textOrNull(item, "front")));
    }

    private Verdict validateNumeric(JsonNode item) {
        String stemReason = textProblem(item, "stem", STEM_MAX);
        if (stemReason != null) {
            return Verdict.drop(stemReason);
        }
        JsonNode answerValue = JsonFields.childOrNull(item, "answerValue");
        if (answerValue == null || !answerValue.isNumber() || !Double.isFinite(answerValue.asDouble())) {
            return Verdict.drop(SCHEMA_INVALID);
        }
        double tolerance = 0;
        JsonNode toleranceNode = JsonFields.childOrNull(item, "tolerance");
        if (toleranceNode != null) {
            if (!toleranceNode.isNumber()
                    || !Double.isFinite(toleranceNode.asDouble())
                    || toleranceNode.asDouble() < 0) {
                return Verdict.drop(SCHEMA_INVALID);
            }
            tolerance = toleranceNode.asDouble();
        }
        String unit = JsonFields.textOrNull(item, "unit");
        if (unit == null) {
            unit = "";
        }
        if (unit.length() > UNIT_MAX) {
            return Verdict.drop(FIELD_TOO_LONG);
        }
        String solutionReason = textProblem(item, "workedSolution", WORKED_SOLUTION_MAX);
        if (solutionReason != null) {
            return Verdict.drop(solutionReason);
        }
        ObjectNode correctRefNode = JSON.createObjectNode();
        correctRefNode.put("value", answerValue.asDouble());
        correctRefNode.put("tolerance", tolerance);
        correctRefNode.put("unit", unit);
        String correctRef = correctRefNode.toString();
        if (correctRef.length() > CORRECT_REF_MAX) {
            return Verdict.drop(FIELD_TOO_LONG);
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("stem", JsonFields.textOrNull(item, "stem"));
        payload.put("answerValue", answerValue.asDouble());
        payload.put("tolerance", tolerance);
        payload.put("unit", unit);
        payload.put("workedSolution", JsonFields.textOrNull(item, "workedSolution"));
        return Verdict.ok(
                new DraftQuestion(GeneratedQuestionType.NUMERIC, payload, correctRef),
                stemKey(JsonFields.textOrNull(item, "stem")));
    }

    private Verdict validateWritten(JsonNode item) {
        String stemReason = textProblem(item, "stem", STEM_MAX);
        if (stemReason != null) {
            return Verdict.drop(stemReason);
        }
        String modelAnswerReason = textProblem(item, "modelAnswer", MODEL_ANSWER_MAX);
        if (modelAnswerReason != null) {
            return Verdict.drop(modelAnswerReason);
        }
        JsonNode rubric = JsonFields.childOrNull(item, "rubric");
        if (rubric == null || !rubric.isArray() || rubric.size() < 3 || rubric.size() > 5) {
            return Verdict.drop(RUBRIC_INVALID);
        }
        ObjectNode payload = JSON.createObjectNode();
        payload.put("stem", JsonFields.textOrNull(item, "stem"));
        payload.put("modelAnswer", JsonFields.textOrNull(item, "modelAnswer"));
        ArrayNode payloadRubric = payload.putArray("rubric");
        int index = 0;
        for (JsonNode criterionNode : rubric) {
            String criterion = JsonFields.textOrNull(criterionNode, "criterion");
            if (criterion == null || criterion.isBlank()) {
                return Verdict.drop(RUBRIC_INVALID);
            }
            if (criterion.length() > CRITERION_MAX) {
                return Verdict.drop(FIELD_TOO_LONG);
            }
            JsonNode maxScore = JsonFields.childOrNull(criterionNode, "maxScore");
            if (maxScore == null
                    || !maxScore.isNumber()
                    || !Double.isFinite(maxScore.asDouble())
                    || maxScore.asDouble() <= 0
                    || maxScore.asDouble() > 100) {
                return Verdict.drop(RUBRIC_INVALID);
            }
            index++;
            ObjectNode entry = payloadRubric.addObject();
            entry.put("id", "crit-" + index);
            entry.put("criterion", criterion);
            entry.put("maxScore", maxScore.asDouble());
        }
        return Verdict.ok(
                new DraftQuestion(GeneratedQuestionType.WRITTEN, payload, "modelAnswer"),
                stemKey(JsonFields.textOrNull(item, "stem")));
    }

    /** Null when the field is a usable string; otherwise the drop reason for it. */
    private static String textProblem(JsonNode item, String field, int maxLength) {
        return textProblem(JsonFields.textOrNull(item, field), maxLength);
    }

    private static String textProblem(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return SCHEMA_INVALID;
        }
        return value.length() > maxLength ? FIELD_TOO_LONG : null;
    }

    private static int countOccurrences(String text, String marker) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = text.indexOf(marker, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + marker.length();
        }
    }

    private static String stemKey(String stem) {
        return normalize(stem);
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
