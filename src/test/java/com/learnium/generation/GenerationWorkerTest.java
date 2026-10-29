package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.common.UserStatus;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.BackgroundJobStatus;
import com.learnium.model.content.ExtractionStatus;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.model.content.Material;
import com.learnium.model.content.MaterialSourceType;
import com.learnium.model.content.Question;
import com.learnium.model.content.Topic;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.QuestionType;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.CommittedDataCleanup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The generation worker in committed state: claim ownership, the
 * deterministic stale-worker race, deletion mid-flight, crash recovery,
 * retry semantics and terminal classifications. Time is a mutable test
 * clock, and the model is a fake — leases expire by advancing the clock,
 * never by sleeping.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GenerationWorkerTest {

    private static final Instant T0 = Instant.parse("2026-10-29T00:00:00Z");
    private static final String TEXT =
            "Fractions represent parts of a whole. One half equals two quarters.";
    private static final String HASH = "a".repeat(64);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A clock the test moves by hand. */
    static final class MutableClock extends Clock {
        private Instant now = T0;

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Programmable generator: behaviour per call number, optionally blocking on one call. */
    static final class FakeGenerator implements QuestionGenerator {
        final AtomicInteger invocations = new AtomicInteger();
        volatile IntFunction<GenerationOutcome> behavior =
                call -> new Failure(GenerationFailureKind.MODEL_ERROR, "UnsetBehaviour");
        volatile int blockOnCall = -1;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        @Override
        public GenerationOutcome generate(GenerationRequest request) {
            int call = invocations.incrementAndGet();
            if (call == blockOnCall) {
                entered.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("blocking call was never released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return behavior.apply(call);
        }

        void reset() throws Exception {
            invocations.set(0);
            blockOnCall = -1;
            entered = null;
            release = null;
            behavior = call -> new Failure(GenerationFailureKind.MODEL_ERROR, "UnsetBehaviour");
        }
    }

    @TestConfiguration
    static class WorkerTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        FakeGenerator fakeGenerator() {
            return new FakeGenerator();
        }
    }

    @Autowired private GenerationWorker worker;
    @Autowired private GenerationClaimService claimService;
    @Autowired private BackgroundJobRepository jobRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private QuestionTypeRepository questionTypeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MutableClock clock;
    @Autowired private FakeGenerator generator;
    @Autowired private ApplicationContext applicationContext;

    @MockitoSpyBean private GenerationPersistenceService persistenceService;

    private UserAccount teacher;
    private Material material;
    private Topic topic;

    @AfterEach
    void cleanUp() throws Exception {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() throws Exception {
        generator.reset();
        clock.set(T0);
        Centre centre = new Centre();
        centre.setName("Worker Centre");
        centre = centreRepository.save(centre);
        teacher = new UserAccount();
        teacher.setCentre(centre);
        teacher.setRole(UserRole.TEACHER);
        teacher.setEmail("worker@alpha.test");
        teacher.setDisplayName("Wanda Worker");
        teacher.setStatus(UserStatus.ACTIVE);
        teacher = userAccountRepository.save(teacher);
        Grade grade = new Grade();
        grade.setCode("G8");
        grade.setName("Grade 8");
        grade = gradeRepository.save(grade);
        Subject subject = new Subject();
        subject.setSubjectName("Mathematics");
        subject = subjectRepository.save(subject);
        GradeSubject gradeSubject = new GradeSubject();
        gradeSubject.setGrade(grade);
        gradeSubject.setSubject(subject);
        gradeSubject = gradeSubjectRepository.save(gradeSubject);
        topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Fractions");
        topic = topicRepository.save(topic);
        // The test profile has no Flyway seed data: create the question
        // type master rows once (they survive the per-test cleanup).
        for (String code : List.of("MCQ", "FILL_BLANK", "FLASHCARD", "NUMERIC", "WRITTEN")) {
            if (questionTypeRepository.findByCode(code).isEmpty()) {
                QuestionType type = new QuestionType();
                type.setCode(code);
                type.setName(code);
                questionTypeRepository.save(type);
            }
        }
        material = new Material();
        material.setCentre(centre);
        material.setUploadedBy(teacher);
        material.setGradeSubject(gradeSubject);
        material.setTitle("Chapter 4: Fractions");
        material.setSourceType(MaterialSourceType.PASTE);
        material.setExtractedText(TEXT);
        material.setContentHash(HASH);
        material.setExtractionStatus(ExtractionStatus.OK);
        material = materialRepository.save(material);
    }

    @Test
    void succeedsAndCommitsDraftsWithProvenance() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":3}");
        generator.behavior =
                call ->
                        new Success(
                                questions(
                                        mcq("What is 1/2 + 1/4?"),
                                        mcq("What is 1/3 + 1/3?"),
                                        "{\"type\":\"MCQ\",\"stem\":\"Broken?\",\"options\":["
                                                + "{\"text\":\"a\"}],\"correctIndex\":0}"),
                                100L,
                                50L,
                                10L,
                                null);

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getUsageInputTokens()).isEqualTo(100L);
        assertThat(saved.getUsageOutputTokens()).isEqualTo(50L);
        assertThat(saved.getUsageThinkingTokens()).isEqualTo(10L);
        var counts = GenerationResultCounts.fromJson(saved.getResultCounts());
        var mcq = counts.forType(GeneratedQuestionType.MCQ);
        assertThat(mcq.requested()).isEqualTo(3);
        assertThat(mcq.received()).isEqualTo(3);
        assertThat(mcq.accepted()).isEqualTo(2);
        assertThat(mcq.dropped()).isEqualTo(1);
        assertThat(mcq.dropReasons()).containsEntry(DraftQuestionFactory.SCHEMA_INVALID, 1);

        List<Question> drafts = questionRepository.findAll();
        assertThat(drafts).hasSize(2);
        assertThat(drafts)
                .allSatisfy(
                        draft -> {
                            assertThat(draft.getGenerationJobId()).isEqualTo(job.getId());
                            assertThat(draft.getGenerationProvider()).isEqualTo("gemini");
                            assertThat(draft.getGeneratedByModel()).isEqualTo("fake-model");
                            assertThat(draft.getPromptVersion()).isEqualTo("gen-prompt-v1");
                            assertThat(draft.getContentHash()).isEqualTo(HASH);
                            assertThat(draft.getCreatedBy()).isEqualTo(teacher.getUserId());
                            assertThat(draft.getTopic().getTopicId()).isEqualTo(topic.getTopicId());
                            assertThat(draft.getDifficulty()).isEqualTo((short) 3);
                        });
        assertThat(worker.runOnce()).isFalse();
    }

    @Test
    void staleWorkerCommitsNothing() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.entered = new CountDownLatch(1);
        generator.release = new CountDownLatch(1);
        generator.blockOnCall = 1;
        generator.behavior =
                call ->
                        new Success(
                                questions(
                                        mcq(
                                                call == 1
                                                        ? "Stale A draft"
                                                        : "Fresh B draft")),
                                10L,
                                5L,
                                null,
                                null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> workerA = executor.submit(() -> worker.runOnce());
            assertThat(generator.entered.await(15, TimeUnit.SECONDS)).isTrue();

            // The lease (5 minutes) lapses while A is stuck in the model call.
            clock.advance(Duration.ofMinutes(6));
            assertThat(worker.runOnce()).isTrue(); // worker B reclaims and commits

            generator.release.countDown();
            assertThat(workerA.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        List<Question> drafts = questionRepository.findAll();
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getPayload().get("stem").asText()).isEqualTo("Fresh B draft");
        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(saved.getAttempts()).isEqualTo(2);
        assertThat(saved.getClaimGeneration()).isEqualTo(2);
        assertThat(GenerationResultCounts.fromJson(saved.getResultCounts()).totalAccepted())
                .isEqualTo(1);
    }

    @Test
    void deletionDuringGenerationFailsTheJobAndCommitsNothing() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.entered = new CountDownLatch(1);
        generator.release = new CountDownLatch(1);
        generator.blockOnCall = 1;
        generator.behavior = call -> new Success(questions(mcq("Too late")), 10L, 5L, null, null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> running = executor.submit(() -> worker.runOnce());
            assertThat(generator.entered.await(15, TimeUnit.SECONDS)).isTrue();

            Material deleted = materialRepository.findById(material.getMaterialId()).orElseThrow();
            deleted.setDeletedAt(Instant.now());
            materialRepository.save(deleted);

            generator.release.countDown();
            assertThat(running.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(questionRepository.findAll()).isEmpty();
        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.MATERIAL_DELETED);
    }

    @Test
    void materialDeletedBeforeExecutionNeverCallsTheModel() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        Material deleted = materialRepository.findById(material.getMaterialId()).orElseThrow();
        deleted.setDeletedAt(Instant.now());
        materialRepository.save(deleted);

        assertThat(worker.runOnce()).isTrue();

        assertThat(generator.invocations).hasValue(0);
        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.MATERIAL_DELETED);
    }

    @Test
    void crashedPersistLeavesTheJobReclaimableAndDraftsCommitExactlyOnce() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior =
                call -> new Success(questions(mcq("Committed once")), 10L, 5L, null, null);
        doThrow(new IllegalStateException("simulated crash"))
                .doCallRealMethod()
                .when(persistenceService)
                .persistSuccess(any(), any(), any());

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob crashed = reload(job);
        assertThat(crashed.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(crashed.getAttempts()).isEqualTo(1);
        assertThat(questionRepository.countByGenerationJobId(job.getId())).isZero();

        clock.advance(Duration.ofMinutes(6));
        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(saved.getAttempts()).isEqualTo(2);
        assertThat(questionRepository.countByGenerationJobId(job.getId())).isEqualTo(1);
    }

    @Test
    void modelErrorReschedulesWithBackoffThenFailsTerminally() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior =
                call -> new Failure(GenerationFailureKind.MODEL_ERROR, "ModelTransportException");

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob rescheduled = reload(job);
        assertThat(rescheduled.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(rescheduled.getAttempts()).isEqualTo(1);
        assertThat(rescheduled.getFailureKind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(rescheduled.getLastErrorClass()).isEqualTo("ModelTransportException");
        assertThat(rescheduled.getNextAttemptAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(worker.runOnce()).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(worker.runOnce()).isTrue();

        BackgroundJob failed = reload(job);
        assertThat(failed.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(2);
        assertThat(failed.getFailureKind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(failed.getFinishedAt()).isNotNull();
    }

    @Test
    void truncatedAndMalformedFailuresAreRetryable() throws Exception {
        BackgroundJob truncatedJob = newJob("{\"MCQ\":1}");
        generator.behavior = call -> new Failure(GenerationFailureKind.TRUNCATED, null);
        assertThat(worker.runOnce()).isTrue();
        BackgroundJob truncated = reload(truncatedJob);
        assertThat(truncated.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(truncated.getFailureKind()).isEqualTo(GenerationFailureKind.TRUNCATED);

        BackgroundJob malformedJob = newJob("{\"MCQ\":1}");
        generator.behavior =
                call -> new Failure(GenerationFailureKind.MALFORMED_RESPONSE, "JsonParseException");
        assertThat(worker.runOnce()).isTrue();
        BackgroundJob malformed = reload(malformedJob);
        assertThat(malformed.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(malformed.getFailureKind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        assertThat(malformed.getLastErrorClass()).isEqualTo("JsonParseException");
    }

    @Test
    void safetyRefusalFailsImmediately() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior = call -> new Failure(GenerationFailureKind.SAFETY_REFUSAL, null);

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.SAFETY_REFUSAL);
        assertThat(saved.getFinishedAt()).isNotNull();
    }

    @Test
    void zeroValidIsTerminalWithCountsAndUsage() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior =
                call ->
                        new Success(
                                questions(
                                        "{\"type\":\"MCQ\",\"stem\":\"Broken?\",\"options\":[],"
                                                + "\"correctIndex\":0}"),
                                10L,
                                5L,
                                null,
                                null);

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.ZERO_VALID);
        assertThat(saved.getUsageInputTokens()).isEqualTo(10L);
        var mcq = GenerationResultCounts.fromJson(saved.getResultCounts())
                .forType(GeneratedQuestionType.MCQ);
        assertThat(mcq.received()).isEqualTo(1);
        assertThat(mcq.accepted()).isZero();
        assertThat(mcq.dropped()).isEqualTo(1);
        assertThat(questionRepository.findAll()).isEmpty();
    }

    @Test
    void generatorExceptionBecomesARetryableModelError() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior =
                call -> {
                    throw new IllegalStateException("adapter bug");
                };

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.MODEL_ERROR);
        assertThat(saved.getLastErrorClass()).isEqualTo("IllegalStateException");
    }

    @Test
    void materialWithNullOrBlankTextFailsAsMaterialDeleted() throws Exception {
        BackgroundJob nullTextJob = newJob("{\"MCQ\":1}");
        Material noText = materialRepository.findById(material.getMaterialId()).orElseThrow();
        noText.setExtractedText(null);
        materialRepository.save(noText);

        assertThat(worker.runOnce()).isTrue();
        assertThat(generator.invocations).hasValue(0);
        BackgroundJob failedNull = reload(nullTextJob);
        assertThat(failedNull.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(failedNull.getFailureKind()).isEqualTo(GenerationFailureKind.MATERIAL_DELETED);

        BackgroundJob blankTextJob = newJob("{\"MCQ\":1}");
        Material blankText = materialRepository.findById(material.getMaterialId()).orElseThrow();
        blankText.setExtractedText("   ");
        materialRepository.save(blankText);

        assertThat(worker.runOnce()).isTrue();
        assertThat(generator.invocations).hasValue(0);
        assertThat(reload(blankTextJob).getFailureKind())
                .isEqualTo(GenerationFailureKind.MATERIAL_DELETED);
    }

    @Test
    void hashChangedDuringGenerationFailsAsMaterialDeleted() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");
        generator.behavior =
                call -> {
                    // The chapter was re-extracted while the model worked:
                    // its content hash no longer matches the frozen one.
                    Material changed =
                            materialRepository
                                    .findById(material.getMaterialId())
                                    .orElseThrow();
                    changed.setContentHash("b".repeat(64));
                    materialRepository.save(changed);
                    return new Success(questions(mcq("Stale source draft")), 10L, 5L, null, null);
                };

        assertThat(worker.runOnce()).isTrue();

        assertThat(questionRepository.findAll()).isEmpty();
        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(saved.getFailureKind()).isEqualTo(GenerationFailureKind.MATERIAL_DELETED);
    }

    @Test
    void difficultyBandsMapOntoTheQuestionScale() throws Exception {
        BackgroundJob easyJob = newJob("{\"MCQ\":1}");
        easyJob.setDifficulty(GenerationDifficulty.EASY);
        jobRepository.save(easyJob);
        generator.behavior =
                call -> new Success(questions(mcq("Easy draft")), 10L, 5L, null, null);
        assertThat(worker.runOnce()).isTrue();
        assertThat(questionRepository.findAll().get(0).getDifficulty()).isEqualTo((short) 1);

        BackgroundJob hardJob = newJob("{\"MCQ\":1}");
        hardJob.setDifficulty(GenerationDifficulty.HARD);
        jobRepository.save(hardJob);
        generator.behavior =
                call -> new Success(questions(mcq("Hard draft")), 10L, 5L, null, null);
        assertThat(worker.runOnce()).isTrue();
        assertThat(
                        questionRepository.findAll().stream()
                                .filter(q -> q.getGenerationJobId().equals(hardJob.getId()))
                                .findFirst()
                                .orElseThrow()
                                .getDifficulty())
                .isEqualTo((short) 5);
    }

    @Test
    void missingQuestionTypeLeavesTheJobRunningForReclaim() throws Exception {
        questionTypeRepository.delete(
                questionTypeRepository.findByCode("WRITTEN").orElseThrow());
        BackgroundJob job = newJob("{\"WRITTEN\":1}");
        generator.behavior =
                call ->
                        new Success(
                                questions(
                                        "{\"type\":\"WRITTEN\",\"stem\":\"Explain fractions.\","
                                                + "\"modelAnswer\":\"Parts of a whole.\","
                                                + "\"rubric\":[{\"criterion\":\"Accuracy\",\"maxScore\":5},"
                                                + "{\"criterion\":\"Reasoning\",\"maxScore\":5},"
                                                + "{\"criterion\":\"Clarity\",\"maxScore\":5}]}"),
                                10L,
                                5L,
                                null,
                                null);

        assertThat(worker.runOnce()).isTrue();

        BackgroundJob saved = reload(job);
        assertThat(saved.getStatus()).isEqualTo(BackgroundJobStatus.RUNNING);
        assertThat(questionRepository.findAll()).isEmpty();
    }

    @Test
    void claimIsExclusiveUntilTheLeaseExpires() throws Exception {
        BackgroundJob job = newJob("{\"MCQ\":1}");

        var first = claimService.claimNext();
        assertThat(first).isPresent();
        assertThat(first.get().jobId()).isEqualTo(job.getId());
        assertThat(first.get().attempts()).isEqualTo(1);
        assertThat(first.get().claimGeneration()).isEqualTo(1);
        assertThat(claimService.claimNext()).isEmpty();

        clock.advance(Duration.ofMinutes(6));
        var second = claimService.claimNext();
        assertThat(second).isPresent();
        assertThat(second.get().attempts()).isEqualTo(2);
        assertThat(second.get().claimGeneration()).isEqualTo(2);
        assertThat(second.get().claimToken()).isNotEqualTo(first.get().claimToken());
    }

    @Test
    void schedulerIsDisabledInTheTestProfile() throws Exception {
        assertThat(applicationContext.getBeansOfType(GenerationScheduler.class)).isEmpty();
    }

    // ---- helpers ------------------------------------------------------------

    private BackgroundJob newJob(String countsJson) {
        BackgroundJob job = new BackgroundJob();
        job.setCentre(material.getCentre());
        job.setMaterial(material);
        job.setContentHash(HASH);
        job.setTopic(topic);
        job.setRequestedCounts(readJson(countsJson));
        job.setDifficulty(GenerationDifficulty.MEDIUM);
        job.setProvider("gemini");
        job.setModel("fake-model");
        job.setPromptVersion("gen-prompt-v1");
        job.setPayloadSchemaVersion("gen-payload-v1");
        job.setNextAttemptAt(clock.instant());
        job.setCreatedBy(teacher.getUserId());
        return jobRepository.save(job);
    }

    private BackgroundJob reload(BackgroundJob job) {
        return jobRepository.findById(job.getId()).orElseThrow();
    }

    private static JsonNode questions(String... items) {
        return readJson("[" + String.join(",", items) + "]");
    }

    private static JsonNode readJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mcq(String stem) {
        ObjectNode item = JSON.createObjectNode();
        item.put("type", "MCQ");
        item.put("stem", stem);
        var options = item.putArray("options");
        options.addObject().put("text", "3/4");
        options.addObject().put("text", "1/2");
        options.addObject().put("text", "2/6");
        options.addObject().put("text", "1/6");
        item.put("correctIndex", 0);
        return item.toString();
    }
}
