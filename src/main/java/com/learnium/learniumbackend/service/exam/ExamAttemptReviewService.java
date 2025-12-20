package com.learnium.learniumbackend.service.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.firebase.auth.FirebaseToken;
import com.learnium.learniumbackend.config.CurrentUserProvider;
import com.learnium.learniumbackend.entity.response.exam.ExamAttemptReviewResponse;
import com.learnium.learniumbackend.exception.NotFoundException;
import com.learnium.learniumbackend.model.v1.exam.ExamAttempt;
import com.learnium.learniumbackend.model.v1.exam.ExamAttemptStatus;
import com.learnium.learniumbackend.model.v1.exam.QuestionAttempt;
import com.learnium.learniumbackend.repository.v1.exam.ExamAttemptRepository;
import com.learnium.learniumbackend.repository.v1.exam.QuestionAttemptRepository;
import com.learnium.learniumbackend.repository.v1.user.UserAccountRepository;
import com.learnium.learniumbackend.util.QuestionPayloadSanitizer;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamAttemptReviewService {

    private final CurrentUserProvider currentUserProvider;
    private final ExamAttemptRepository attemptRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final QuestionPayloadSanitizer sanitizer;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public ExamAttemptReviewResponse getReview(UUID attemptId) {
        FirebaseToken firebaseToken = currentUserProvider.getUserId();
        UUID userId = null;
        if(ObjectUtils.isNotEmpty(firebaseToken) && ObjectUtils.isNotEmpty(firebaseToken.getClaims())
            && firebaseToken.getClaims().containsKey("user_id")) {
            String authUid = String.valueOf(firebaseToken.getClaims().get("user_id"));
            userId = userAccountRepository.findIdByAuthId(authUid).orElse(null);

        }
        if(userId == null) {
            throw new ValidationException("Unauthenticated request");
        }

        ExamAttempt attempt = attemptRepository.findByAttemptIdAndUser_UserId(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("Exam attempt not found: " + attemptId));

        // Optional: only allow review if submitted
        if (attempt.getStatus() != ExamAttemptStatus.SUBMITTED) {
            throw new ValidationException("Attempt is not submitted yet: " + attemptId);
        }

        List<QuestionAttempt> qaList = questionAttemptRepository.findForReview(attemptId);

        var questionItems = qaList.stream()
                .map(qa -> {
                    var q = qa.getQuestion();

                    String typeName = (q.getQuestionType() != null) ? q.getQuestionType().getName() : "";
                    String type = normalizeType(typeName);

                    String correctRef = q.getCorrectRef(); // stored in DB
                    correctRef = correctRef == null ? "" : correctRef;

                    String correctOptionId = null;
                    List<String> acceptedAnswers = Collections.emptyList();

                    if (type.contains("MCQ")) {
                        // MCQ: return correct option id only (A/B/C/D)
                        correctOptionId = StringUtils.isBlank(correctRef) ? null : correctRef.trim();
                    } else if (type.contains("FILL")) {
                        // Fill blank: return accepted answers list
                        acceptedAnswers = Arrays.stream(correctRef.split("\\|"))
                                .map(String::trim)
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.toList());
                    }

                    JsonNode sanitizedPayload = sanitizer.sanitize(q.getPayload());

                    return ExamAttemptReviewResponse.QuestionReviewItem.builder()
                            .questionAttemptId(qa.getQuestionAttemptId())
                            .questionId(q.getQuestionId())
                            .questionType(typeName)
                            .difficulty(qa.getDifficulty())
                            .questionPayload(String.valueOf(sanitizedPayload))
                            .userAnswer(String.valueOf(qa.getAnswerPayload()))

                            .correctOptionId(correctOptionId)
                            .acceptedAnswers(acceptedAnswers)

                            .correct(qa.isCorrect())
                            .points(qa.getPoints())
                            .timeSpentSec(qa.getTimeSpentSec())
                            .build();
                })
                .toList();

        return ExamAttemptReviewResponse.builder()
                .attemptId(attempt.getAttemptId())

                .examTemplateId(attempt.getExamTemplate().getExamTemplateId())
                .templateName(attempt.getExamTemplate().getTemplateName())
                .templateVersion(attempt.getTemplateVersion())

                .gradeId(attempt.getGrade().getGradeId())
                .subjectId(attempt.getSubject().getSubjectId())
                .topicId(attempt.getTopic() != null ? attempt.getTopic().getTopicId() : null)
                .examModeId(attempt.getExamMode().getExamModeId())

                .status(attempt.getStatus().name())
                .durationSec(attempt.getDurationSec())

                .totalQuestions(attempt.getTotalQuestions())
                .attempted(attempt.getAttemptedQuestions())
                .correct(attempt.getCorrectCount())
                .wrong(attempt.getWrongCount())
                .skipped(attempt.getSkippedCount())

                .score(attempt.getScore())
                .percentage(attempt.getPercentage())

                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())

                .questions(questionItems)
                .build();
    }

    private String normalizeType(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }
}