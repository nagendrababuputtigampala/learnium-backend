package com.learnium.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.admin.BatchAccessGuard;
import com.learnium.api.dto.TestDtos.*;
import com.learnium.common.error.*;
import com.learnium.model.common.ExamModeType;
import com.learnium.model.common.UserStatus;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.*;
import com.learnium.repository.content.*;
import com.learnium.repository.exam.*;
import com.learnium.repository.master.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestService {
    private final ExamTemplateRepository templates;
    private final ExamTemplateQuestionRepository templateItems;
    private final ExamInstanceRepository instances;
    private final ExamInstanceQuestionRepository frozenItems;
    private final ExamInstanceStudentRepository assigned;
    private final QuestionRepository questions;
    private final GradeSubjectRepository pairings;
    private final ExamModeRepository modes;
    private final BatchMembershipRepository memberships;
    private final UserAccountRepository users;
    private final CentreRepository centres;
    private final BatchAccessGuard batches;
    private final JoinCodes codes;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper json;

    public TestService(ExamTemplateRepository templates, ExamTemplateQuestionRepository templateItems,
            ExamInstanceRepository instances, ExamInstanceQuestionRepository frozenItems,
            ExamInstanceStudentRepository assigned, QuestionRepository questions, GradeSubjectRepository pairings,
            ExamModeRepository modes, BatchMembershipRepository memberships, UserAccountRepository users,
            CentreRepository centres, BatchAccessGuard batches, JoinCodes codes, Clock clock,
            tools.jackson.databind.ObjectMapper json) {
        this.templates = templates; this.templateItems = templateItems; this.instances = instances;
        this.frozenItems = frozenItems; this.assigned = assigned; this.questions = questions; this.pairings = pairings;
        this.modes = modes; this.memberships = memberships; this.users = users; this.centres = centres;
        this.batches = batches; this.codes = codes; this.clock = clock; this.json = json;
    }

    @Transactional(readOnly = true)
    public List<DefinitionDto> list(LearniumPrincipal caller) {
        var rows = templates.visible(caller.centreId(), caller.userId(), admin(caller));
        if (rows.isEmpty()) return List.of();
        var items = templateItems.items(caller.centreId(), rows.stream().map(ExamTemplate::getExamTemplateId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getExamTemplate().getExamTemplateId()));
        var counts = instances.publicationCounts(caller.centreId()).stream()
                .collect(Collectors.toMap(ExamInstanceRepository.PublicationCount::getTestId, ExamInstanceRepository.PublicationCount::getCount));
        return rows.stream().map(t -> definition(t, items.getOrDefault(t.getExamTemplateId(), List.of()),
                counts.getOrDefault(t.getExamTemplateId(), 0L), false)).toList();
    }

    @Transactional
    public DefinitionDto get(LearniumPrincipal caller, UUID id) {
        return detail(owned(caller, id));
    }

    @Transactional
    public DefinitionDto create(LearniumPrincipal caller, DefinitionRequest request) {
        ExamTemplate template = new ExamTemplate();
        template.setCentre(centres.getReferenceById(caller.centreId()));
        template.setCreatedBy(caller.userId());
        replace(caller, template, request);
        return detail(template);
    }

    @Transactional
    public DefinitionDto update(LearniumPrincipal caller, UUID id, DefinitionRequest request) {
        ExamTemplate template = owned(caller, id);
        replace(caller, template, request);
        return detail(template);
    }

    @Transactional
    public void delete(LearniumPrincipal caller, UUID id) {
        ExamTemplate template = owned(caller, id);
        if (instances.countByExamTemplateExamTemplateId(id) > 0) throw new BusinessException(ErrorCode.TST_PUBLISHED);
        templateItems.deleteAll(items(template));
        templateItems.flush();
        templates.delete(template);
    }

    private void replace(LearniumPrincipal caller, ExamTemplate template, DefinitionRequest request) {
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 120)
            throw invalid(ErrorCode.TST_INVALID, "name", "Enter a test name of 1–120 characters");
        if (!Set.of("SCORED_TEST", "FLASHCARD_PRACTICE").contains(String.valueOf(request.kind())))
            throw invalid(ErrorCode.TST_INVALID, "kind", "Choose a scored test or flashcard practice");
        GradeSubject pairing = Optional.ofNullable(request.gradeSubjectId()).flatMap(pairings::findById)
                .filter(GradeSubject::isActive).orElseThrow(() -> invalid(ErrorCode.TST_INVALID, "gradeSubjectId", "Choose an active grade and subject"));
        var selected = validateItems(caller, request.items(), pairing.getGradeSubjectId(), request.kind());
        if (template.getExamTemplateId() != null) {
            templateItems.deleteAll(items(template)); templateItems.flush();
        }
        template.setTemplateName(request.name().trim()); template.setGradeSubject(pairing);
        template.setGrade(pairing.getGrade()); template.setSubject(pairing.getSubject());
        template.setKind(request.kind()); template.setTopic(null);
        template.setExamMode(modes.findByTestType("FLASHCARD_PRACTICE".equals(request.kind()) ? ExamModeType.flashcards : ExamModeType.quiz).orElseThrow());
        template.setRules(new ObjectMapper().createObjectNode());
        templates.saveAndFlush(template);
        for (int i = 0; i < selected.size(); i++) {
            var choice = selected.get(i);
            ExamTemplateQuestion item = new ExamTemplateQuestion();
            item.setCentreId(caller.centreId()); item.setExamTemplate(template); item.setQuestion(choice.question());
            item.setQuestionVersion(choice.question().getVersion()); item.setDisplayOrder(i);
            item.setMarks(choice.marks()); templateItems.save(item);
        }
        templateItems.flush();
    }

    private record Selection(Question question, BigDecimal marks) {}
    private List<Selection> validateItems(LearniumPrincipal caller, List<ItemRequest> input, UUID pairingId, String kind) {
        if (input == null || input.isEmpty() || input.size() > 200)
            throw invalid(ErrorCode.TST_INVALID, "items", "Choose between 1 and 200 questions");
        var ids = input.stream().filter(Objects::nonNull).map(ItemRequest::questionId).filter(Objects::nonNull).toList();
        var available = questions.approvedForTest(caller.centreId(), ids).stream()
                .collect(Collectors.toMap(Question::getQuestionId, q -> q));
        Set<UUID> seen = new HashSet<>();
        List<Selection> selected = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemRequest entry = input.get(i);
            Question q = entry == null ? null : available.get(entry.questionId());
            if (q == null || !seen.add(q.getQuestionId()) || !q.getTopic().getGradeSubject().getGradeSubjectId().equals(pairingId)
                    || !("FLASHCARD_PRACTICE".equals(kind) ? "FLASHCARD".equals(q.getQuestionType().getCode()) : Set.of("MCQ", "FILL_BLANK", "NUMERIC", "WRITTEN").contains(q.getQuestionType().getCode())))
                throw invalid(ErrorCode.TST_INVALID, "items[" + i + "].questionId", "Choose a distinct approved question of this grade, subject and kind");
            selected.add(new Selection(q, marks(entry.marks(), kind, "items[" + i + "].marks")));
        }
        return selected;
    }

    private BigDecimal marks(BigDecimal value, String kind, String field) {
        if ("FLASHCARD_PRACTICE".equals(kind) && value == null) return BigDecimal.ZERO.setScale(2);
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2 || value.compareTo(new BigDecimal("9999.99")) > 0
                || ("SCORED_TEST".equals(kind) ? value.signum() == 0 : value.signum() != 0))
            throw invalid(ErrorCode.TST_INVALID, field, "Use positive marks with up to two decimal places; flashcards use zero");
        return value.setScale(2);
    }

    @Transactional
    public InstanceDto publish(LearniumPrincipal caller, UUID id, PublishRequest request) {
        ExamTemplate template = owned(caller, id);
        Batch batch = batch(caller, request.batchId());
        if (!batch.isActive() || !batch.getGradeSubject().getGradeSubjectId().equals(template.getGradeSubject().getGradeSubjectId()))
            throw invalid(ErrorCode.TST_PUBLISH_INVALID, "batchId", "Choose an active batch for this grade and subject");
        Instant now = persistentInstant(clock.instant());
        Instant opens = persistentInstant(request.opensAt() == null ? now : request.opensAt());
        Instant closes = persistentInstant(request.closesAt());
        if (closes == null || !closes.isAfter(opens))
            throw invalid(ErrorCode.TST_PUBLISH_INVALID, "closesAt", "Closing time is required and must be after opening time");
        if (request.timeLimitSeconds() != null && request.timeLimitSeconds() <= 0)
            throw invalid(ErrorCode.TST_PUBLISH_INVALID, "timeLimitSeconds", "Time limit must be positive");
        int maxAttempts = request.maxAttempts() == null ? 1 : request.maxAttempts();
        if (maxAttempts < 1) throw invalid(ErrorCode.TST_PUBLISH_INVALID, "maxAttempts", "Allow at least one attempt");
        String release = request.answerReleasePolicy() == null ? "AFTER_CLOSE" : request.answerReleasePolicy();
        String camera = request.cameraMode() == null ? "OFF" : request.cameraMode();
        if (!Set.of("AFTER_CLOSE", "ON_SUBMIT", "NEVER").contains(release))
            throw invalid(ErrorCode.TST_PUBLISH_INVALID, "answerReleasePolicy", "Choose an answer release policy");
        if (!Set.of("OFF", "ON").contains(camera)) throw invalid(ErrorCode.TST_PUBLISH_INVALID, "cameraMode", "Choose OFF or ON");
        var roster = activeRoster(caller.centreId(), batch.getBatchId());
        if (roster.isEmpty()) throw invalid(ErrorCode.TST_PUBLISH_INVALID, "batchId", "Add an active student to the batch before publishing");
        List<ItemRequest> input = request.items() == null ? items(template).stream()
                .map(item -> new ItemRequest(item.getQuestion().getQuestionId(), item.getMarks())).toList() : request.items();
        Map<UUID, BigDecimal> overrides = request.itemMarks() == null ? Map.of() : request.itemMarks();
        var ids = input.stream().filter(Objects::nonNull).map(ItemRequest::questionId).collect(Collectors.toSet());
        if (!ids.containsAll(overrides.keySet())) throw invalid(ErrorCode.TST_INVALID, "itemMarks", "Overrides must refer to selected questions");
        input = input.stream().map(item -> item == null ? null : new ItemRequest(item.questionId(), overrides.getOrDefault(item.questionId(), item.marks()))).toList();
        var selected = validateItems(caller, input, template.getGradeSubject().getGradeSubjectId(), template.getKind());
        ExamInstance instance = new ExamInstance();
        instance.setCentreId(caller.centreId()); instance.setExamTemplate(template); instance.setBatch(batch);
        instance.setPublishedBy(users.getReferenceById(caller.userId())); instance.setCreatedBy(caller.userId());
        instance.setDefinitionName(template.getTemplateName()); instance.setDefinitionKind(template.getKind());
        instance.setJoinCode(codes.next()); instance.setOpensAt(opens); instance.setClosesAt(closes);
        instance.setStatus(now.isBefore(opens) ? ExamInstanceStatus.SCHEDULED : now.isBefore(closes) ? ExamInstanceStatus.OPEN : ExamInstanceStatus.CLOSED);
        instance.setTimeLimitSeconds(request.timeLimitSeconds()); instance.setMaxAttempts(maxAttempts);
        instance.setRetakesAllowed(maxAttempts > 1); instance.setAnswerReleasePolicy(release);
        instance.setIntegritySignalsOn(Boolean.TRUE.equals(request.integrityEnabled())); instance.setCameraMode(CameraMode.valueOf(camera));
        instances.saveAndFlush(instance);
        for (int order = 0; order < selected.size(); order++) {
            var choice = selected.get(order); var q = choice.question();
            ExamInstanceQuestion frozen = new ExamInstanceQuestion();
            frozen.setCentreId(caller.centreId()); frozen.setExamInstance(instance); frozen.setQuestion(q);
            frozen.setQuestionOrder(order); frozen.setQuestionVersion(q.getVersion()); frozen.setMarks(choice.marks());
            frozen.setPayloadSnapshot(q.getPayload().deepCopy()); frozen.setCorrectRefSnapshot(q.getCorrectRef());
            frozenItems.save(frozen);
        }
        for (BatchMembership membership : roster) addAssigned(instance, membership.getUser(), caller.userId());
        return instanceDto(instance, true);
    }

    @Transactional(readOnly = true)
    public List<InstanceDto> instances(LearniumPrincipal caller, UUID batchId) {
        if (batchId != null) batch(caller, batchId);
        return instances.manageable(caller.centreId(), caller.userId(), admin(caller), batchId).stream()
                .map(i -> instanceDto(i, false)).toList();
    }

    @Transactional
    public InstanceDto instance(LearniumPrincipal caller, UUID id) { return instanceDto(manageable(caller, id), true); }

    @Transactional
    public InstanceDto regenerate(LearniumPrincipal caller, UUID id) {
        var instance = manageable(caller, id); instance.setJoinCode(codes.next());
        instances.saveAndFlush(instance); return instanceDto(instance, true);
    }

    @Transactional
    public InstanceDto close(LearniumPrincipal caller, UUID id) {
        var instance = manageable(caller, id);
        if (instance.getClosedAt() == null) instance.setClosedAt(persistentInstant(clock.instant()));
        instance.setStatus(ExamInstanceStatus.CLOSED); instances.saveAndFlush(instance);
        return instanceDto(instance, true);
    }

    @Transactional
    public InstanceDto addStudent(LearniumPrincipal caller, UUID id, StudentRequest request) {
        var instance = manageable(caller, id);
        if ("CLOSED".equals(status(instance))) throw invalid(ErrorCode.TST_PUBLISH_INVALID, "userId", "Students cannot be added to a closed instance");
        var member = activeRoster(caller.centreId(), instance.getBatch().getBatchId()).stream()
                .filter(m -> m.getUser().getUserId().equals(request.userId())).findFirst()
                .orElseThrow(() -> invalid(ErrorCode.TST_PUBLISH_INVALID, "userId", "Choose an active student from this batch roster"));
        if (!assigned.existsByExamInstanceExamInstanceIdAndUserUserId(id, request.userId()))
            addAssigned(instance, member.getUser(), caller.userId());
        return instanceDto(instance, true);
    }

    private List<BatchMembership> activeRoster(UUID centreId, UUID batchId) {
        return memberships.findByCentreIdAndBatchIdAndMemberRoleAndStatus(centreId, batchId, MemberRole.STUDENT, MembershipStatus.ACTIVE)
                .stream().filter(m -> m.getUser().getStatus() == UserStatus.ACTIVE && m.getUser().getRole() == UserRole.STUDENT).toList();
    }
    private void addAssigned(ExamInstance instance, UserAccount user, UUID actor) {
        var student = new ExamInstanceStudent(); student.setCentreId(instance.getCentreId()); student.setExamInstance(instance);
        student.setUser(user); student.setAddedBy(actor); student.setAddedAt(persistentInstant(clock.instant())); assigned.saveAndFlush(student);
    }
    private ExamTemplate owned(LearniumPrincipal caller, UUID id) {
        return templates.owned(caller.centreId(), caller.userId(), admin(caller), id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
    }
    private Batch batch(LearniumPrincipal caller, UUID id) {
        if (id == null) throw invalid(ErrorCode.TST_PUBLISH_INVALID, "batchId", "Choose a batch");
        try { return batches.requireBatchAccess(caller, id); }
        catch (BusinessException e) { throw new BusinessException(ErrorCode.TST_NOT_FOUND); }
    }
    private ExamInstance manageable(LearniumPrincipal caller, UUID id) {
        var instance = instances.lockInCentre(caller.centreId(), id).orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        batch(caller, instance.getBatch().getBatchId()); return instance;
    }
    private boolean admin(LearniumPrincipal caller) { return caller.role() == UserRole.CENTRE_ADMIN; }
    private BusinessException invalid(ErrorCode code, String field, String reason) { return new BusinessException(code, code.getDefaultMessage(), Map.of(field, reason)); }
    private Instant persistentInstant(Instant value) { return value == null ? null : value.truncatedTo(ChronoUnit.MICROS); }
    private List<ExamTemplateQuestion> items(ExamTemplate t) { return templateItems.items(t.getCentre().getCentreId(), List.of(t.getExamTemplateId())); }
    private DefinitionDto detail(ExamTemplate template) { return definition(template, items(template), instances.countByExamTemplateExamTemplateId(template.getExamTemplateId()), true); }
    private DefinitionDto definition(ExamTemplate t, List<ExamTemplateQuestion> items, long count, boolean full) {
        return new DefinitionDto(t.getExamTemplateId(), t.getTemplateName(), t.getGradeSubject().getGradeSubjectId(),
                t.getGrade().getName(), t.getSubject().getSubjectName(), t.getKind(), t.getCreatedBy(), items.size(),
                items.stream().map(ExamTemplateQuestion::getMarks).reduce(BigDecimal.ZERO, BigDecimal::add), count,
                full ? items.stream().map(i -> item(i.getQuestion(), i.getMarks(), i.getDisplayOrder(), i.getQuestion().getPayload(), i.getQuestion().getCorrectRef())).toList() : List.of());
    }
    private ItemDto item(Question q, BigDecimal marks, int order, com.fasterxml.jackson.databind.JsonNode payload, String correctRef) {
        return new ItemDto(q.getQuestionId(), q.getQuestionType().getCode(), q.getTopic().getTopicName(), marks, order, json.readTree(payload.toString()), correctRef);
    }
    private String status(ExamInstance i) {
        if (i.getClosedAt() != null || i.getStatus() == ExamInstanceStatus.CLOSED || !clock.instant().isBefore(i.getClosesAt())) return "CLOSED";
        return clock.instant().isBefore(i.getOpensAt()) ? "SCHEDULED" : "OPEN";
    }
    private InstanceDto instanceDto(ExamInstance i, boolean full) {
        return new InstanceDto(i.getExamInstanceId(), i.getExamTemplate().getExamTemplateId(), i.getDefinitionName(), i.getDefinitionKind(),
                i.getBatch().getBatchId(), i.getBatch().getName(), i.getJoinCode(), status(i), i.getOpensAt(), i.getClosesAt(), i.getClosedAt(),
                i.getTimeLimitSeconds(), i.getMaxAttempts(), i.getAnswerReleasePolicy(), i.isIntegritySignalsOn(), i.getCameraMode().name(),
                full ? frozenItems.frozen(i.getCentreId(), i.getExamInstanceId()).stream().map(f -> item(f.getQuestion(), f.getMarks(), f.getQuestionOrder(), f.getPayloadSnapshot(), f.getCorrectRefSnapshot())).toList() : List.of(),
                full ? assigned.assigned(i.getCentreId(), i.getExamInstanceId()).stream().map(s -> new AssignedStudent(s.getUser().getUserId(), s.getUser().getDisplayName(), s.getAddedBy(), s.getAddedAt())).toList() : List.of());
    }
}
