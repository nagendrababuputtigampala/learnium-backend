package com.learnium.learniumbackend.service.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.learniumbackend.config.CurrentUserProvider;
import com.learnium.learniumbackend.entity.request.exam.SubmitExamAttemptRequest;
import com.learnium.learniumbackend.entity.response.exam.SubmitExamAttemptResponse;
import com.learnium.learniumbackend.exception.NotFoundException;
import com.learnium.learniumbackend.model.v1.content.ExamTemplateQuestion;
import com.learnium.learniumbackend.model.v1.content.Question;
import com.learnium.learniumbackend.model.v1.exam.*;
import com.learnium.learniumbackend.repository.v1.content.ExamTemplateQuestionRepository;
import com.learnium.learniumbackend.repository.v1.content.QuestionRepository;
import com.learnium.learniumbackend.repository.v1.exam.ExamAttemptRepository;
import com.learnium.learniumbackend.repository.v1.exam.ExamTemplateRepository;
import com.learnium.learniumbackend.repository.v1.exam.QuestionAttemptRepository;
import com.learnium.learniumbackend.repository.v1.user.UserAccountRepository;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    private final CurrentUserProvider currentUserProvider;

    private final ExamAttemptRepository attemptRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final ExamTemplateRepository examTemplateRepository;
    private final ExamTemplateQuestionRepository examTemplateQuestionRepository;
    private final QuestionRepository questionRepository;
    private final UserAccountRepository userAccountRepository;

    private final ObjectMapper objectMapper;

    @Transactional
    public SubmitExamAttemptResponse submit(UUID attemptId, SubmitExamAttemptRequest request) {
        if (attemptId == null) throw new ValidationException("attemptId is required");
        if (request == null) throw new ValidationException("request body is required");

        UUID userId = request.getUserId();
        Instant now = Instant.now();

        // 1) Load by PK first (prevents NonUniqueObjectException)
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElse(null);

        if (attempt != null) {
            UUID ownerId = attempt.getUser().getUserId();
            if (!ownerId.equals(userId)) {
                throw new ValidationException("attemptId already exists and does not belong to current user");
            }
            if (attempt.getStatus() == ExamAttemptStatus.SUBMITTED) {
                throw new ValidationException("Attempt already submitted: " + attemptId);
            }
        } else {
            // Auto-create attempt only at submit (your no-resume design)
            if (request.getExamTemplateId() == null) {
                throw new ValidationException("examTemplateId is required when creating a new attempt");
            }

            ExamTemplate template = examTemplateRepository.findById(request.getExamTemplateId())
                    .orElseThrow(() -> new NotFoundException("Exam template not found: " + request.getExamTemplateId()));

            ExamAttempt newAttempt = new ExamAttempt();
            newAttempt.setAttemptId(attemptId);
            newAttempt.setUser(userAccountRepository.getReferenceById(userId));
            newAttempt.setExamTemplate(template);

            // denormalized for dashboards
            newAttempt.setGrade(template.getGrade());
            newAttempt.setSubject(template.getSubject());
            newAttempt.setTopic(template.getTopic());
            newAttempt.setExamMode(template.getExamMode());

            newAttempt.setTemplateVersion(template.getVersion());
            newAttempt.setStartedAt(now);
            newAttempt.setStatus(ExamAttemptStatus.SUBMITTED);

            // Save & keep managed instance (prevents TransientPropertyValueException)
            attempt = attemptRepository.saveAndFlush(newAttempt);
        }

        UUID templateId = attempt.getExamTemplate().getExamTemplateId();

        // 2) Allowed questions for this template
        List<ExamTemplateQuestion> etqList = examTemplateQuestionRepository.findActiveByTemplateIdOrdered(templateId);
        if (etqList.isEmpty()) {
            throw new ValidationException("No questions configured for template: " + templateId);
        }

        Map<UUID, ExamTemplateQuestion> allowed = etqList.stream()
                .collect(Collectors.toMap(etq -> etq.getQuestion().getQuestionId(), Function.identity()));

        int totalQuestions = etqList.size();

        // 3) Request answers
        List<SubmitExamAttemptRequest.AnswerItem> answers =
                request.getAnswers() == null ? List.of() : request.getAnswers();

        answers = answers.stream()
                .filter(Objects::nonNull)
                .filter(a -> a.getQuestionId() != null)
                .toList();

        // Validate answered questionIds belong to template
        for (var a : answers) {
            if (!allowed.containsKey(a.getQuestionId())) {
                throw new ValidationException("Question does not belong to template. questionId=" + a.getQuestionId());
            }
        }

        // 4) Load Question entities for provided answers
        List<UUID> answeredIds = answers.stream()
                .map(SubmitExamAttemptRequest.AnswerItem::getQuestionId)
                .distinct()
                .toList();

        Map<UUID, Question> questionMap = answeredIds.isEmpty()
                ? Map.of()
                : questionRepository.findByQuestionIdIn(answeredIds).stream()
                .collect(Collectors.toMap(Question::getQuestionId, Function.identity()));

        // 5) Score + persist question_attempts
        int correct = 0;
        int wrong = 0;
        int skippedByEmptyAnswer = 0;
        int score = 0;

        List<QuestionAttempt> qaEntities = new ArrayList<>(answers.size());

        for (var a : answers) {
            Question q = questionMap.get(a.getQuestionId());
            if (q == null) throw new ValidationException("Question not found: " + a.getQuestionId());
            if (!q.isActive()) throw new ValidationException("Question inactive: " + a.getQuestionId());

            ExamTemplateQuestion etq = allowed.get(a.getQuestionId());

            JsonNode answerNode = mapToJsonNode(a.getAnswer());
            AnswerEval eval = evaluateAnswerByTypeName(q, answerNode);

            QuestionAttempt qa = new QuestionAttempt();
            qa.setAttempt(attempt);
            qa.setQuestion(q);
            qa.setQuestionVersion(etq.getQuestionVersion());
            qa.setDifficulty(q.getDifficulty());
            qa.setAnswerPayload(answerNode);
            qa.setTimeSpentSec(a.getTimeSpentSec());
            qa.setPoints(etq.getPoints());

            if (eval.skipped) {
                skippedByEmptyAnswer++;
                qa.setCorrect(false);
            } else {
                qa.setCorrect(eval.correct);
                if (eval.correct) {
                    correct++;
                    score += etq.getPoints();
                } else {
                    wrong++;
                }
            }

            qaEntities.add(qa);
        }

        if (!qaEntities.isEmpty()) {
            questionAttemptRepository.saveAll(qaEntities);
        }

        // skippedTotal = omitted + explicitly empty answers
        int attemptedRowsFromRequest = answers.size();
        int omitted = Math.max(0, totalQuestions - attemptedRowsFromRequest);
        int skippedTotal = omitted + skippedByEmptyAnswer;

        double percentage = totalQuestions == 0 ? 0.0 : (correct * 100.0) / totalQuestions;

        // 6) Update attempt summary
        attempt.setTotalQuestions(totalQuestions);

        // attempted = user actually tried (non-empty)
        attempt.setAttemptedQuestions(correct + wrong);

        attempt.setCorrectCount(correct);
        attempt.setWrongCount(wrong);
        attempt.setSkippedCount(skippedTotal);
        attempt.setScore(score);
        attempt.setPercentage(percentage);
        attempt.setDurationSec(request.getDurationSec());
        attempt.markSubmitted(now);

        attemptRepository.save(attempt);

        return SubmitExamAttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .status(attempt.getStatus().name())
                .totalQuestions(totalQuestions)
                .attempted(attempt.getAttemptedQuestions())
                .correct(correct)
                .wrong(wrong)
                .skipped(skippedTotal)
                .score(score)
                .percentage(percentage)
                .submittedAt(attempt.getSubmittedAt())
                .build();
    }

    // ---------------- helpers ----------------

    private JsonNode mapToJsonNode(Map<String, Object> answer) {
        if (answer == null) return objectMapper.nullNode();
        return objectMapper.valueToTree(answer);
    }

    private static class AnswerEval {
        final boolean correct;
        final boolean skipped;

        private AnswerEval(boolean correct, boolean skipped) {
            this.correct = correct;
            this.skipped = skipped;
        }

        static AnswerEval skipped() { return new AnswerEval(false, true); }
        static AnswerEval correct() { return new AnswerEval(true, false); }
        static AnswerEval wrong() { return new AnswerEval(false, false); }
    }

    /**
     * Uses QuestionType.name (since your QuestionType has no "code")
     * Expected names you can store in DB:
     * - "MCQ"
     * - "FILL_BLANK"
     * - "FILL_IN_THE_BLANKS"
     * - "FILL IN THE BLANKS"
     */
    private AnswerEval evaluateAnswerByTypeName(Question q, JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull() || answerNode.isMissingNode()) {
            return AnswerEval.skipped();
        }

        String correctRef = q.getCorrectRef();
        if (StringUtils.isBlank(correctRef)) {
            return AnswerEval.wrong();
        }

        String typeName = (q.getQuestionType() != null) ? q.getQuestionType().getName() : null;
        typeName = normalizeType(typeName);

        if (typeName.contains("MCQ")) {
            return evalMcq(correctRef, answerNode);
        }

        if (typeName.contains("FILL")) {
            return evalFillBlank(correctRef, answerNode);
        }

        return evalFallback(correctRef, answerNode);
    }

    private String normalizeType(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }

    private AnswerEval evalMcq(String correctRef, JsonNode answerNode) {
        String selected = answerNode.path("selectedOptionId").asText(null);
        if (StringUtils.isBlank(selected)) return AnswerEval.skipped();
        return normalize(correctRef).equals(normalize(selected)) ? AnswerEval.correct() : AnswerEval.wrong();
    }

    private AnswerEval evalFillBlank(String correctRef, JsonNode answerNode) {
        String text = answerNode.path("text").asText(null);
        if (text == null) text = answerNode.path("value").asText(null);

        if (StringUtils.isBlank(text)) return AnswerEval.skipped();

        String userNorm = normalize(text);

        // Allow multiple accepted answers stored like "12|twelve|12.0"
        String[] accepted = correctRef.split("\\|");
        for (String a : accepted) {
            if (normalize(a).equals(userNorm)) return AnswerEval.correct();
        }
        return AnswerEval.wrong();
    }

    private AnswerEval evalFallback(String correctRef, JsonNode answerNode) {
        String user = extractGeneric(answerNode);
        if (StringUtils.isBlank(user)) return AnswerEval.skipped();
        return normalize(correctRef).equals(normalize(user)) ? AnswerEval.correct() : AnswerEval.wrong();
    }

    private String extractGeneric(JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull()) return null;
        if (answerNode.isTextual()) return answerNode.asText();

        if (answerNode.isObject()) {
            JsonNode sel = answerNode.get("selectedOptionId");
            if (sel != null && sel.isTextual()) return sel.asText();

            JsonNode txt = answerNode.get("text");
            if (txt != null && txt.isTextual()) return txt.asText();

            JsonNode val = answerNode.get("value");
            if (val != null && val.isTextual()) return val.asText();
        }
        return answerNode.asText(null);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}