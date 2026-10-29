package com.learnium.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.api.dto.GenerationDtos.GenerateRequest;
import com.learnium.api.dto.GenerationDtos.GeneratedQuestionDto;
import com.learnium.api.dto.GenerationDtos.GenerationJobDetailDto;
import com.learnium.api.dto.GenerationDtos.GenerationJobDto;
import com.learnium.api.dto.GenerationDtos.TypeResultDto;
import com.learnium.common.api.PageResponse;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.ExtractionStatus;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.Material;
import com.learnium.model.content.Question;
import com.learnium.model.content.Topic;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.security.LearniumPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generation jobs from the API side: creation (with the brief's
 * validation matrix and daily caps) and the read paths (job polling,
 * draft previews). Execution lives in {@link GenerationWorker}.
 *
 * <p>Creation freezes everything the worker needs onto the job row —
 * material, its content hash, topic, counts, difficulty, provider/model
 * and contract versions — so a queued job's meaning cannot drift when
 * configuration changes afterwards. Material visibility mirrors the
 * materials API exactly: creator or centre admin, else a 404 that
 * never leaks existence across teachers or centres.
 */
@Service
public class GenerationJobService {

    static final int MAX_TOTAL_PER_JOB = 40;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BackgroundJobRepository jobRepository;
    private final MaterialRepository materialRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final GenerationProperties properties;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper webJson;

    public GenerationJobService(
            BackgroundJobRepository jobRepository,
            MaterialRepository materialRepository,
            TopicRepository topicRepository,
            QuestionRepository questionRepository,
            GenerationProperties properties,
            Clock clock,
            tools.jackson.databind.ObjectMapper webJson) {
        this.jobRepository = jobRepository;
        this.materialRepository = materialRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.properties = properties;
        this.clock = clock;
        this.webJson = webJson;
    }

    @Transactional
    public GenerationJobDto createJob(
            LearniumPrincipal principal, UUID materialId, GenerateRequest request) {
        Material material = requireVisibleMaterial(principal, materialId);
        if (material.getExtractionStatus() != ExtractionStatus.OK) {
            throw new BusinessException(ErrorCode.GEN_NOT_GENERATABLE);
        }
        Topic topic = requireTopicOnMaterial(request, material);
        EnumMap<GeneratedQuestionType, Integer> counts = parseCounts(request.counts());
        GenerationDifficulty difficulty = parseDifficulty(request.difficulty());
        enforceDailyCaps(principal);

        BackgroundJob job = new BackgroundJob();
        job.setCentre(material.getCentre());
        job.setMaterial(material);
        job.setContentHash(material.getContentHash());
        job.setTopic(topic);
        job.setRequestedCounts(countsToJson(counts));
        job.setDifficulty(difficulty);
        job.setProvider(properties.getProvider());
        job.setModel(properties.activeModel());
        job.setPromptVersion(GenerationContract.PROMPT_VERSION);
        job.setPayloadSchemaVersion(GenerationContract.PAYLOAD_SCHEMA_VERSION);
        job.setNextAttemptAt(clock.instant());
        // The auditor is empty outside request threads (and the worker has
        // no security context at all), so the creator is set explicitly.
        job.setCreatedBy(principal.userId());
        return toDto(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public GenerationJobDetailDto getJob(LearniumPrincipal principal, UUID jobId) {
        BackgroundJob job =
                jobRepository
                        .findByIdAndCentreId(jobId, principal.centreId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.GEN_JOB_NOT_FOUND));
        if (principal.role() == UserRole.TEACHER && !principal.userId().equals(job.getCreatedBy())) {
            throw new BusinessException(ErrorCode.GEN_JOB_NOT_FOUND);
        }
        return toDetailDto(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<GeneratedQuestionDto> listMaterialQuestions(
            LearniumPrincipal principal, UUID materialId, Pageable pageable) {
        requireVisibleMaterial(principal, materialId);
        Pageable newestFirst =
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
        var page =
                questionRepository.findByCentreCentreIdAndMaterialMaterialId(
                        principal.centreId(), materialId, newestFirst);
        return PageResponse.from(page, this::toQuestionDto);
    }

    // ---- validation helpers -------------------------------------------------

    /** Same rule as the materials API: creator or centre admin, else 404. */
    private Material requireVisibleMaterial(LearniumPrincipal principal, UUID materialId) {
        Material material =
                materialRepository
                        .findVisibleById(materialId, principal.centreId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MAT_NOT_FOUND));
        if (principal.role() == UserRole.TEACHER
                && !material.getUploadedBy().getUserId().equals(principal.userId())) {
            throw new BusinessException(ErrorCode.MAT_NOT_FOUND);
        }
        return material;
    }

    private Topic requireTopicOnMaterial(GenerateRequest request, Material material) {
        Topic topic =
                request.topicId() == null
                        ? null
                        : topicRepository.findById(request.topicId()).orElse(null);
        if (topic == null
                || !topic.getGradeSubject()
                        .getGradeSubjectId()
                        .equals(material.getGradeSubject().getGradeSubjectId())) {
            throw new BusinessException(ErrorCode.GEN_TOPIC_MISMATCH);
        }
        return topic;
    }

    private EnumMap<GeneratedQuestionType, Integer> parseCounts(Map<String, Integer> requested) {
        EnumMap<GeneratedQuestionType, Integer> counts = new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            counts.put(type, 0);
        }
        if (requested != null) {
            for (Map.Entry<String, Integer> entry : requested.entrySet()) {
                GeneratedQuestionType type;
                try {
                    type = GeneratedQuestionType.valueOf(entry.getKey());
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new BusinessException(ErrorCode.GEN_COUNT_INVALID);
                }
                Integer value = entry.getValue();
                if (value == null || value < 0 || value > MAX_TOTAL_PER_JOB) {
                    throw new BusinessException(ErrorCode.GEN_COUNT_INVALID);
                }
                counts.put(type, value);
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 1 || total > MAX_TOTAL_PER_JOB) {
            throw new BusinessException(ErrorCode.GEN_COUNT_INVALID);
        }
        return counts;
    }

    private GenerationDifficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return GenerationDifficulty.MEDIUM;
        }
        try {
            return GenerationDifficulty.valueOf(difficulty.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VAL_FAILED, "difficulty must be one of EASY, MEDIUM, HARD");
        }
    }

    private void enforceDailyCaps(LearniumPrincipal principal) {
        Instant dayStart =
                LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        long teacherCount =
                jobRepository.countByCentreCentreIdAndCreatedByAndCreatedAtGreaterThanEqual(
                        principal.centreId(), principal.userId(), dayStart);
        if (teacherCount >= properties.getMaxJobsPerTeacherPerDay()) {
            throw new BusinessException(ErrorCode.GEN_DAILY_LIMIT);
        }
        long centreCount =
                jobRepository.countByCentreCentreIdAndCreatedAtGreaterThanEqual(
                        principal.centreId(), dayStart);
        if (centreCount >= properties.getMaxJobsPerCentrePerDay()) {
            throw new BusinessException(ErrorCode.GEN_DAILY_LIMIT);
        }
    }

    // ---- mapping ------------------------------------------------------------

    private static JsonNode countsToJson(EnumMap<GeneratedQuestionType, Integer> counts) {
        ObjectNode node = JSON.createObjectNode();
        counts.forEach((type, count) -> node.put(type.name(), count));
        return node;
    }

    static Map<String, Integer> countsFromJson(JsonNode json) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            JsonNode value = json == null ? null : json.get(type.name());
            counts.put(type.name(), value == null ? 0 : value.asInt());
        }
        return counts;
    }

    static GenerationJobDto toDto(BackgroundJob job) {
        return new GenerationJobDto(
                job.getId(),
                job.getMaterial().getMaterialId(),
                job.getTopic().getTopicId(),
                job.getStatus().name(),
                countsFromJson(job.getRequestedCounts()),
                job.getDifficulty().name(),
                job.getProvider(),
                job.getModel(),
                job.getAttempts(),
                job.getCreatedAt());
    }

    private static GenerationJobDetailDto toDetailDto(BackgroundJob job) {
        Map<String, TypeResultDto> resultCounts = null;
        if (job.getResultCounts() != null) {
            Map<String, TypeResultDto> mapped = new LinkedHashMap<>();
            GenerationResultCounts counts = GenerationResultCounts.fromJson(job.getResultCounts());
            counts.byType()
                    .forEach(
                            (type, result) ->
                                    mapped.put(
                                            type.name(),
                                            new TypeResultDto(
                                                    result.requested(),
                                                    result.received(),
                                                    result.accepted(),
                                                    result.dropped(),
                                                    result.dropReasons())));
            resultCounts = mapped;
        }
        return new GenerationJobDetailDto(
                job.getId(),
                job.getMaterial().getMaterialId(),
                job.getTopic().getTopicId(),
                job.getStatus().name(),
                countsFromJson(job.getRequestedCounts()),
                job.getDifficulty().name(),
                job.getProvider(),
                job.getModel(),
                job.getPromptVersion(),
                job.getPayloadSchemaVersion(),
                job.getContentHash(),
                job.getFailureKind() == null ? null : job.getFailureKind().name(),
                resultCounts,
                job.getUsageInputTokens(),
                job.getUsageOutputTokens(),
                job.getUsageThinkingTokens(),
                job.getAttempts(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getFinishedAt());
    }

    private GeneratedQuestionDto toQuestionDto(Question question) {
        return new GeneratedQuestionDto(
                question.getQuestionId(),
                question.getQuestionType().getCode(),
                question.getTopic().getTopicId(),
                toWebJson(question.getPayload()),
                question.getCorrectRef(),
                question.getGenerationProvider(),
                question.getGeneratedByModel(),
                question.getPromptVersion(),
                question.getGenerationJobId(),
                question.getCreatedAt());
    }

    /** Entity JSON is Jackson 2; the web layer renders Jackson 3 — bridge by text. */
    private tools.jackson.databind.JsonNode toWebJson(JsonNode payload) {
        try {
            return webJson.readTree(JSON.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored question payload is not valid JSON", e);
        }
    }
}
