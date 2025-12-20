package com.learnium.learniumbackend.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.learniumbackend.entity.response.content.QuestionResponse;
import com.learnium.learniumbackend.entity.response.content.TestDetailResponse;
import com.learnium.learniumbackend.exception.NotFoundException;
import com.learnium.learniumbackend.model.v1.content.Topic;
import com.learnium.learniumbackend.model.v1.exam.ExamTemplate;
import com.learnium.learniumbackend.repository.v1.content.ExamTemplateQuestionRepository;
import com.learnium.learniumbackend.repository.v1.exam.ExamTemplateRepository;
import com.learnium.learniumbackend.util.QuestionPayloadSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestService {

    private final ExamTemplateRepository examTemplateRepository;
    private final ExamTemplateQuestionRepository examTemplateQuestionRepository;
    private final QuestionPayloadSanitizer sanitizer;

    @Transactional(readOnly = true)
    public TestDetailResponse getTest(UUID templateId) {
        ExamTemplate template = examTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Test template not found: " + templateId));

        var items = examTemplateQuestionRepository.findActiveQuestionsByTemplateId(templateId);

        List<QuestionResponse> questions = items.stream()
                .map(etq -> QuestionResponse.builder()
                        .questionId(etq.getQuestion().getQuestionId())
                        .payload(String.valueOf(sanitizer.sanitize(etq.getQuestion().getPayload())))
                        .build())
                .toList();


        // Topic is nullable in template
        Topic topic = template.getTopic();

        // Derive duration/totalQuestions from rules JSON (safe defaults)
        int durationSeconds = extractInt(template.getRules(), "time_limit_sec", 0);
        int totalQuestions = extractInt(template.getRules(), "totalQuestions", questions.size());

        return TestDetailResponse.builder()
                .examTemplateId(template.getExamTemplateId())
                .templateName(template.getTemplateName())
                .description(template.getDescription())
                .durationSeconds(durationSeconds)
                .totalQuestions(totalQuestions)

                .gradeId(template.getGrade().getGradeId())
                .subjectId(template.getSubject().getSubjectId())
                .topicId(topic != null ? topic.getTopicId() : null)
                .examModeId(template.getExamMode().getExamModeId())

                .version(template.getVersion())

                .questions(questions)
                .build();
    }

    private int extractInt(JsonNode node, String field, int defaultValue) {
        if (node == null || node.isNull()) return defaultValue;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return defaultValue;
        return v.isInt() ? v.asInt() : defaultValue;
    }
}