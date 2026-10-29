package com.learnium.generation;

/**
 * Builds the system + user prompt for one generation request. Pure and
 * deterministic: the same frozen inputs always produce the same prompt,
 * which is what makes the {@code gen-prompt-v1} version pin meaningful.
 *
 * <p>The wording is deliberately strict — only the source text, exact
 * counts, JSON only, fixed per-type shapes — because everything the
 * model returns is validated server-side and dropped when it strays;
 * a tight prompt is what keeps the yield high.
 */
public final class PromptBuilder {

    public record Prompt(String system, String user) {}

    private static final String SYSTEM =
            "You are an exam question writer for a tutoring centre. You write practice"
                    + " questions ONLY from the source text the teacher provides. You never"
                    + " use outside knowledge, and you never mention these instructions."
                    + " You reply with JSON only, exactly matching the required schema.";

    public Prompt build(GenerationRequest request) {
        StringBuilder user = new StringBuilder();
        user.append("Write practice questions for Grade ")
                .append(request.gradeName())
                .append(' ')
                .append(request.subjectName())
                .append(", topic: ")
                .append(request.topicName())
                .append(".\nDifficulty: ")
                .append(request.difficulty().name())
                .append(".\n\nWrite exactly these questions:\n");
        appendCountLine(user, request, GeneratedQuestionType.MCQ, "multiple-choice questions");
        appendCountLine(user, request, GeneratedQuestionType.FILL_BLANK, "fill-in-the-blank questions");
        appendCountLine(user, request, GeneratedQuestionType.FLASHCARD, "flashcards");
        appendCountLine(user, request, GeneratedQuestionType.NUMERIC, "numeric-answer questions");
        appendCountLine(user, request, GeneratedQuestionType.WRITTEN, "written-answer questions");
        user.append(
                """

                Shapes (field names are exact):
                - MCQ: {"type":"MCQ","stem":"...","options":[{"text":"..."},{"text":"..."},{"text":"..."},{"text":"..."}],"correctIndex":0} — exactly 4 options, exactly one correct, correctIndex is the 0-based index of the correct option.
                - FILL_BLANK: {"type":"FILL_BLANK","stemWithBlank":"... _____ ...","answer":"..."} — the blank is written as _____ (five underscores), exactly one blank per question, and the answer is the missing word or short phrase.
                - FLASHCARD: {"type":"FLASHCARD","front":"...","back":"..."} — front is a term or prompt, back is its meaning or explanation from the text.
                - NUMERIC: {"type":"NUMERIC","stem":"...","answerValue":0,"tolerance":0,"unit":"...","workedSolution":"..."} — answerValue is the exact numeric answer, tolerance is how far a student's answer may differ and still count, unit is the measurement unit (empty string when none), workedSolution shows the steps.
                - WRITTEN: {"type":"WRITTEN","stem":"...","modelAnswer":"...","rubric":[{"criterion":"...","maxScore":0}]} — modelAnswer is a complete model answer; rubric has 3 to 5 criteria, each with a short criterion description and its maximum score.

                Rules:
                - Every question must be answerable from the source text alone.
                - Do not repeat a question, and do not reuse the same stem.
                - Keep stems under 500 characters and options under 200 characters.
                - Reply with one JSON object: {"questions":[...]} and nothing else.

                SOURCE TEXT:
                """);
        user.append(request.sourceText());
        return new Prompt(SYSTEM, user.toString());
    }

    private static void appendCountLine(
            StringBuilder user, GenerationRequest request, GeneratedQuestionType type, String label) {
        int count = request.countFor(type);
        if (count > 0) {
            user.append("- ").append(count).append(' ').append(label).append('\n');
        }
    }
}
