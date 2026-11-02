package com.learnium.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.api.dto.QuestionDtos.*;
import com.learnium.common.api.PageResponse;
import com.learnium.common.error.*;
import com.learnium.generation.*;
import com.learnium.model.content.*;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.*;
import com.learnium.security.LearniumPrincipal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionReviewService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final tools.jackson.databind.ObjectMapper webJson;
    private final Clock clock;
    private final DraftQuestionFactory validator = new DraftQuestionFactory();

    public QuestionReviewService(QuestionRepository questions, TopicRepository topics,
            tools.jackson.databind.ObjectMapper webJson, Clock clock) {
        this.questions = questions;
        this.topics = topics;
        this.webJson = webJson;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionListDto> list(LearniumPrincipal caller, String status,
            UUID materialId, UUID gradeSubjectId, UUID gradeId, UUID subjectId,
            UUID topicId, String type, String difficulty, Pageable pageable) {
        QuestionStatus parsedStatus;
        Short parsedDifficulty;
        try {
            parsedStatus = QuestionStatus.valueOf(status);
            if (type != null) GeneratedQuestionType.valueOf(type);
            parsedDifficulty = difficulty == null ? null : Short.valueOf(difficulty);
            if (parsedDifficulty != null && (parsedDifficulty < 1 || parsedDifficulty > 5)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VAL_FAILED, "Invalid question filters");
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("questionId")));
        return PageResponse.from(questions.search(caller.centreId(), caller.userId(), isAdmin(caller),
                null, parsedStatus, materialId, gradeSubjectId, gradeId, subjectId, topicId,
                type, parsedDifficulty, page), row -> listDto(row, caller));
    }

    @Transactional(readOnly = true)
    public QuestionDto get(LearniumPrincipal caller, UUID id) {
        return questions.search(caller.centreId(), caller.userId(), isAdmin(caller), id,
                        null, null, null, null, null, null, null, null, PageRequest.of(0, 1))
                .stream().findFirst().map(row -> dto(row, caller))
                .orElseThrow(() -> new BusinessException(ErrorCode.QST_NOT_FOUND));
    }

    @Transactional
    public QuestionDto edit(LearniumPrincipal caller, UUID id, EditRequest request) {
        Question question = requireVisible(caller, id);
        requireDraft(question, request.revision());
        if ((request.type() != null && !request.type().equals(question.getQuestionType().getCode()))
                || (request.gradeSubjectId() != null && !request.gradeSubjectId().equals(
                        question.getTopic().getGradeSubject().getGradeSubjectId()))) {
            throw badRequest("content", "Question type and grade/subject cannot be changed");
        }
        Topic topic = topics.findById(request.topicId())
                .orElseThrow(() -> badRequest("topicId", "Choose a valid topic"));
        if (!topic.getGradeSubject().getGradeSubjectId().equals(
                question.getTopic().getGradeSubject().getGradeSubjectId()) || !topic.isActive()) {
            throw badRequest("topicId", "Topic must be active and belong to the original grade and subject");
        }
        com.fasterxml.jackson.databind.JsonNode content = JSON.valueToTree(request.content());
        var validated = validate(question, content);
        if (question.getQuestionType().getCode().equals("WRITTEN")) {
            preserveRubricIds(question, content, validated.payload());
        }
        question.setTopic(topic);
        question.setDifficulty(request.difficulty());
        question.setPayload(validated.payload());
        question.setCorrectRef(validated.correctRef());
        question.setVersion(question.getVersion() + 1);
        questions.saveAndFlush(question);
        return get(caller, id);
    }

    @Transactional
    public QuestionDto decide(LearniumPrincipal caller, UUID id, long revision, boolean approve) {
        Question question = requireVisible(caller, id);
        requireDraft(question, revision);
        if (approve) {
            com.fasterxml.jackson.databind.JsonNode content = question.getPayload().deepCopy();
            if ("MCQ".equals(question.getQuestionType().getCode()) && content.isObject()) {
                int index = -1;
                for (int i = 0; i < content.path("options").size(); i++) {
                    if (Objects.equals(question.getCorrectRef(), content.path("options").get(i).path("id").asText())) index = i;
                }
                ((ObjectNode) content).put("correctIndex", index);
            }
            var validated = validate(question, content);
            if (question.getQuestionType().getCode().equals("WRITTEN")) {
                preserveRubricIds(question, content, validated.payload());
            }
            // Approval persists the shared validator's canonical representation too,
            // so legacy blank/tolerance normalization cannot leave an approved malformed payload.
            question.setPayload(validated.payload());
            question.setCorrectRef(validated.correctRef());
            question.setStatus(QuestionStatus.APPROVED);
            question.setApprovedBy(caller.userId());
            question.setApprovedAt(clock.instant());
        } else {
            question.setStatus(QuestionStatus.REJECTED);
        }
        questions.saveAndFlush(question);
        return get(caller, id);
    }

    @Transactional
    public QuestionDto fork(LearniumPrincipal caller, UUID id) {
        Question original = requireVisible(caller, id);
        if (original.getStatus() != QuestionStatus.APPROVED) {
            throw new BusinessException(ErrorCode.QST_IMMUTABLE, "Only approved questions can be forked");
        }
        Question copy = new Question();
        copy.setCentre(original.getCentre());
        copy.setTopic(original.getTopic());
        copy.setQuestionType(original.getQuestionType());
        copy.setDifficulty(original.getDifficulty());
        copy.setPayload(original.getPayload().deepCopy());
        copy.setCorrectRef(original.getCorrectRef());
        copy.setSourceQuestionId(original.getQuestionId());
        // The shared bank does not grant access to another teacher's private PDF.
        copy.setCreatedBy(caller.userId());
        copy.setGenerationJobId(original.getGenerationJobId());
        copy.setGenerationProvider(original.getGenerationProvider());
        copy.setGeneratedByModel(original.getGeneratedByModel());
        copy.setPromptVersion(original.getPromptVersion());
        copy.setPayloadSchemaVersion(original.getPayloadSchemaVersion());
        copy.setContentHash(original.getContentHash());
        questions.saveAndFlush(copy);
        return get(caller, copy.getQuestionId());
    }

    private Question requireVisible(LearniumPrincipal caller, UUID id) {
        Question question = questions.findForReview(id, caller.centreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QST_NOT_FOUND));
        if (!question.isActive() || (question.getStatus() != QuestionStatus.APPROVED
                && !isAdmin(caller) && !caller.userId().equals(question.getCreatedBy()))) {
            throw new BusinessException(ErrorCode.QST_NOT_FOUND);
        }
        return question;
    }

    private void requireDraft(Question question, long revision) {
        if (question.getStatus() != QuestionStatus.DRAFT) throw new BusinessException(ErrorCode.QST_IMMUTABLE);
        if (question.getRevision() != revision) throw new BusinessException(ErrorCode.QST_STALE);
    }

    private DraftQuestionFactory.DraftQuestion validate(Question question, com.fasterxml.jackson.databind.JsonNode content) {
        if (!content.isObject()) throw invalid("content", "Content must be a question object");
        String type = question.getQuestionType().getCode();
        if (content.has("type") && !type.equals(content.path("type").asText())) {
            throw badRequest("content.type", "Question type cannot be changed");
        }
        var outcome = validator.validateForReview(GeneratedQuestionType.valueOf(type), content);
        if (outcome.draft() == null) {
            String message = switch (outcome.reason()) {
                case DraftQuestionFactory.FIELD_TOO_LONG -> "The value exceeds the allowed length";
                case DraftQuestionFactory.RUBRIC_INVALID -> "Use 3–5 criteria with positive scores up to 100";
                case DraftQuestionFactory.ANSWER_KEY_INVALID -> "Choose one of the four options as the correct answer";
                default -> switch (outcome.field()) {
                    case "options" -> "Use four distinct, non-empty options";
                    case "stemWithBlank" -> "Enter text with exactly one _____ blank";
                    case "answerValue" -> "Enter a finite numeric answer";
                    case "tolerance" -> "Enter a finite, non-negative tolerance";
                    default -> "This field is required and must contain text";
                };
            };
            throw invalid("content." + outcome.field(), message);
        }
        return outcome.draft();
    }

    private BusinessException badRequest(String field, String message) {
        return new BusinessException(ErrorCode.VAL_FAILED, "Validation failed", Map.of(field, message));
    }

    private QuestionListDto listDto(QuestionSummary q, LearniumPrincipal caller) {
        var payload = q.getPayload();
        String field = switch (q.getType()) {
            case "FILL_BLANK" -> "stemWithBlank";
            case "FLASHCARD" -> "front";
            default -> "stem";
        };
        String preview = payload.path(field).asText("");
        boolean sourceVisible = isAdmin(caller) || caller.userId().equals(q.getCreatedBy());
        return new QuestionListDto(q.getQuestionId(), q.getType(), q.getStatus().name(),
                preview.substring(0, Math.min(preview.length(), 200)), q.getGradeSubjectId(),
                q.getGradeName(), q.getSubjectName(), q.getTopicId(), q.getTopicName(), q.getDifficulty(),
                sourceVisible ? q.getMaterialId() : null, sourceVisible ? q.getMaterialTitle() : null,
                q.getCreatedBy(), q.getCreatedAt(), q.getApprovedBy(), q.getApprovedAt());
    }

    private BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.QST_INVALID, "Question validation failed", Map.of(field, message));
    }

    private void preserveRubricIds(Question question, com.fasterxml.jackson.databind.JsonNode input,
            com.fasterxml.jackson.databind.JsonNode output) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        question.getPayload().path("rubric").forEach(entry -> existing.add(entry.path("id").asText()));
        java.util.Set<String> assigned = new java.util.HashSet<>();
        int sequence = 1;
        for (int i = 0; i < input.path("rubric").size(); i++) {
            String id;
            var entry = input.path("rubric").get(i);
            if (entry.has("id")) {
                id = entry.path("id").asText();
                if (!existing.contains(id) || !assigned.add(id)) {
                    throw invalid("content.rubric", "Criterion IDs must be unique and belong to this draft");
                }
            } else {
                while (existing.contains("crit-" + sequence) || assigned.contains("crit-" + sequence)) sequence++;
                id = "crit-" + sequence;
                assigned.add(id);
            }
            ((ObjectNode) output.path("rubric").get(i)).put("id", id);
        }
    }

    private boolean isAdmin(LearniumPrincipal caller) {
        return caller.role() == UserRole.CENTRE_ADMIN;
    }

    private QuestionDto dto(QuestionSummary q, LearniumPrincipal caller) {
        boolean sourceVisible = isAdmin(caller) || caller.userId().equals(q.getCreatedBy());
        return new QuestionDto(q.getQuestionId(), q.getType(), q.getStatus().name(), q.getGradeSubjectId(),
                q.getGradeName(), q.getSubjectName(), q.getTopicId(), q.getTopicName(), q.getDifficulty(),
                webJson.readTree(q.getPayload().toString()), q.getCorrectRef(), q.getRevision(), q.getVersion(),
                q.getCreatedBy(), q.getCreatedAt(), q.getApprovedBy(), q.getApprovedAt(), q.getSourceQuestionId(),
                sourceVisible ? q.getMaterialId() : null, sourceVisible ? q.getMaterialTitle() : null,
                q.getProvider(), q.getModel(), q.getPromptVersion(), q.getGenerationJobId());
    }
}
