package com.learnium.model.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.config.JpaAuditingConfig;
import com.learnium.model.common.ExamModeType;
import com.learnium.model.common.XpSourceType;
import com.learnium.model.content.ExamTemplateQuestion;
import com.learnium.model.content.Question;
import com.learnium.model.content.QuestionStatus;
import com.learnium.model.content.Topic;
import com.learnium.model.gamification.Badge;
import com.learnium.model.gamification.UserBadges;
import com.learnium.model.gamification.UserTopicStat;
import com.learnium.model.gamification.UserXpLedger;
import com.learnium.model.master.ExamMode;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.QuestionType;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.ExamTemplateQuestionRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.ExamInstanceQuestionRepository;
import com.learnium.repository.exam.ExamInstanceRepository;
import com.learnium.repository.exam.ExamTemplateRepository;
import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.repository.gamification.BadgeRepository;
import com.learnium.repository.gamification.UserBadgesRepository;
import com.learnium.repository.gamification.UserTopicStatRepository;
import com.learnium.repository.gamification.UserXpLedgerRepository;
import com.learnium.repository.master.ExamModeRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ExamChainPersistenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private QuestionTypeRepository questionTypeRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private ExamModeRepository examModeRepository;
    @Autowired private ExamTemplateRepository examTemplateRepository;
    @Autowired private ExamTemplateQuestionRepository examTemplateQuestionRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private ExamInstanceRepository examInstanceRepository;
    @Autowired private ExamInstanceQuestionRepository examInstanceQuestionRepository;
    @Autowired private ExamAttemptRepository examAttemptRepository;
    @Autowired private QuestionAttemptRepository questionAttemptRepository;
    @Autowired private UserTopicStatRepository userTopicStatRepository;
    @Autowired private BadgeRepository badgeRepository;
    @Autowired private UserBadgesRepository userBadgesRepository;
    @Autowired private UserXpLedgerRepository userXpLedgerRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void fullChainFromTemplateToMarkedAnswers() throws Exception {
        Centre centre = new Centre();
        centre.setName("Alpha Tutoring");
        centre = centreRepository.save(centre);

        UserAccount teacher = new UserAccount();
        teacher.setCentre(centre);
        teacher.setRole(UserRole.TEACHER);
        teacher.setEmail("patel@example.com");
        teacher.setDisplayName("Mrs. Patel");
        teacher = userAccountRepository.save(teacher);

        UserAccount student = new UserAccount();
        student.setCentre(centre);
        student.setRole(UserRole.STUDENT);
        student.setDisplayName("Simran");
        student = userAccountRepository.save(student);

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

        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Fractions");
        topic = topicRepository.save(topic);

        QuestionType mcqType = new QuestionType();
        mcqType.setCode("MCQ");
        mcqType.setName("Multiple Choice");
        mcqType = questionTypeRepository.save(mcqType);

        QuestionType writtenType = new QuestionType();
        writtenType.setCode("WRITTEN");
        writtenType.setName("Written Answer");
        writtenType = questionTypeRepository.save(writtenType);

        Question mcq = new Question();
        mcq.setCentre(centre);
        mcq.setTopic(topic);
        mcq.setQuestionType(mcqType);
        mcq.setPayload(objectMapper.readTree("{\"prompt\":\"1/2 + 1/4?\"}"));
        mcq.setCorrectRef("options[0]");
        mcq.setStatus(QuestionStatus.APPROVED);
        mcq = questionRepository.save(mcq);

        Question written = new Question();
        written.setCentre(centre);
        written.setTopic(topic);
        written.setQuestionType(writtenType);
        written.setPayload(objectMapper.readTree("{\"prompt\":\"Explain equivalent fractions.\"}"));
        written.setCorrectRef("rubric");
        written.setStatus(QuestionStatus.APPROVED);
        written = questionRepository.save(written);

        ExamMode mode = new ExamMode();
        mode.setTestType(ExamModeType.quiz);
        mode.setTitle("Quiz");
        mode = examModeRepository.save(mode);
        assertThat(examModeRepository.findByTestType(ExamModeType.quiz)).isPresent();
        assertThat(examModeRepository.findByTestType(ExamModeType.games)).isEmpty();

        ExamTemplate template = new ExamTemplate();
        template.setCentre(centre);
        template.setGrade(grade);
        template.setSubject(subject);
        template.setTopic(topic);
        template.setExamMode(mode);
        template.setTemplateName("Fractions Check");
        template.setRules(objectMapper.readTree("{\"timeLimitMin\":20}"));
        template = examTemplateRepository.save(template);
        assertThat(examTemplateRepository.findByCentreId(centre.getCentreId()))
                .extracting(ExamTemplate::getTemplateName)
                .containsExactly("Fractions Check");

        ExamTemplateQuestion tq1 = new ExamTemplateQuestion();
        tq1.setCentreId(centre.getCentreId());
        tq1.setExamTemplate(template);
        tq1.setQuestion(written);
        tq1.setQuestionVersion(written.getVersion());
        tq1.setDisplayOrder(2);
        examTemplateQuestionRepository.save(tq1);

        ExamTemplateQuestion tq2 = new ExamTemplateQuestion();
        tq2.setCentreId(centre.getCentreId());
        tq2.setExamTemplate(template);
        tq2.setQuestion(mcq);
        tq2.setQuestionVersion(mcq.getVersion());
        tq2.setDisplayOrder(1);
        examTemplateQuestionRepository.save(tq2);

        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName("Grade 8 Math — Batch B");
        batch = batchRepository.save(batch);

        ExamInstance instance = new ExamInstance();
        instance.setCentreId(centre.getCentreId());
        instance.setExamTemplate(template);
        instance.setBatch(batch);
        instance.setPublishedBy(teacher);
        instance.setJoinCode("FRAC-4821");
        instance.setOpensAt(Instant.parse("2026-10-28T15:00:00Z"));
        instance.setClosesAt(Instant.parse("2026-10-28T16:00:00Z"));
        instance.setIntegritySignalsOn(true);
        instance.setCameraMode(CameraMode.VERIFY_SNAPSHOTS);
        instance = examInstanceRepository.save(instance);

        ExamInstanceQuestion iq1 = new ExamInstanceQuestion();
        iq1.setCentreId(centre.getCentreId());
        iq1.setExamInstance(instance);
        iq1.setQuestion(mcq);
        iq1.setQuestionOrder(1);
        iq1.setQuestionVersion(mcq.getVersion());
        examInstanceQuestionRepository.save(iq1);

        ExamInstanceQuestion iq2 = new ExamInstanceQuestion();
        iq2.setCentreId(centre.getCentreId());
        iq2.setExamInstance(instance);
        iq2.setQuestion(written);
        iq2.setQuestionOrder(2);
        iq2.setQuestionVersion(written.getVersion());
        examInstanceQuestionRepository.save(iq2);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setCentreId(centre.getCentreId());
        attempt.setExamInstance(instance);
        attempt.setUser(student);
        attempt.setStartedAt(Instant.parse("2026-10-28T15:05:00Z"));
        attempt.setTotalQuestions(2);
        attempt = examAttemptRepository.save(attempt);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.STARTED);
        assertThat(attempt.getTotalScore()).isNull();

        QuestionAttempt objective = new QuestionAttempt();
        objective.setCentreId(centre.getCentreId());
        objective.setAttempt(attempt);
        objective.setQuestion(mcq);
        objective.setQuestionVersion(mcq.getVersion());
        objective.setAnswerPayload(objectMapper.readTree("{\"selected\":\"options[0]\"}"));
        objective.setCorrect(Boolean.TRUE);
        questionAttemptRepository.save(objective);

        QuestionAttempt writtenAnswer = new QuestionAttempt();
        writtenAnswer.setCentreId(centre.getCentreId());
        writtenAnswer.setAttempt(attempt);
        writtenAnswer.setQuestion(written);
        writtenAnswer.setQuestionVersion(written.getVersion());
        writtenAnswer.setAnswerPayload(objectMapper.readTree("{\"text\":\"Fractions with same value.\"}"));
        writtenAnswer.setAiSuggestedScore(new BigDecimal("3.50"));
        writtenAnswer.setMarkingStatus(MarkingStatus.MARKING_PENDING);
        questionAttemptRepository.save(writtenAnswer);

        // A retake is a second row distinguished by attempt_no.
        ExamAttempt retake = new ExamAttempt();
        retake.setCentreId(centre.getCentreId());
        retake.setExamInstance(instance);
        retake.setUser(student);
        retake.setAttemptNo(2);
        retake.setStatus(ExamAttemptStatus.INVALIDATED);
        retake.setStartedAt(Instant.parse("2026-10-28T15:20:00Z"));
        examAttemptRepository.save(retake);

        entityManager.flush();
        entityManager.clear();

        assertThat(
                        examTemplateQuestionRepository
                                .findByCentreIdAndExamTemplateIdOrderByDisplayOrderAsc(
                                        centre.getCentreId(), template.getExamTemplateId()))
                .extracting(ExamTemplateQuestion::getDisplayOrder)
                .containsExactly(1, 2);
        assertThat(examInstanceRepository.findByCentreIdAndBatchId(centre.getCentreId(), batch.getBatchId()))
                .extracting(ExamInstance::getJoinCode)
                .containsExactly("FRAC-4821");
        ExamInstance reloadedInstance = examInstanceRepository.findByJoinCode("FRAC-4821").orElseThrow();
        assertThat(reloadedInstance.getCameraMode()).isEqualTo(CameraMode.VERIFY_SNAPSHOTS);
        assertThat(reloadedInstance.isIntegritySignalsOn()).isTrue();
        assertThat(reloadedInstance.getStatus()).isEqualTo(ExamInstanceStatus.PUBLISHED);
        assertThat(examInstanceRepository.findByJoinCode("NOPE-0000")).isEmpty();
        assertThat(
                        examInstanceQuestionRepository
                                .findByCentreIdAndExamInstanceIdOrderByQuestionOrderAsc(
                                        centre.getCentreId(), instance.getExamInstanceId()))
                .extracting(ExamInstanceQuestion::getQuestionOrder)
                .containsExactly(1, 2);
        assertThat(
                        examAttemptRepository.findByCentreIdAndExamInstanceId(
                                centre.getCentreId(), instance.getExamInstanceId()))
                .hasSize(2);

        var answers =
                questionAttemptRepository.findByCentreIdAndAttemptId(
                        centre.getCentreId(), attempt.getAttemptId());
        assertThat(answers).hasSize(2);
        QuestionAttempt reloadedWritten =
                answers.stream()
                        .filter(a -> a.getMarkingStatus() == MarkingStatus.MARKING_PENDING)
                        .findFirst()
                        .orElseThrow();
        assertThat(reloadedWritten.getCorrect()).isNull();
        assertThat(reloadedWritten.getAiSuggestedScore()).isEqualByComparingTo("3.50");
        assertThat(reloadedWritten.getConfirmedScore()).isNull();
        QuestionAttempt reloadedObjective =
                answers.stream()
                        .filter(a -> a.getMarkingStatus() == MarkingStatus.AUTO)
                        .findFirst()
                        .orElseThrow();
        assertThat(reloadedObjective.getCorrect()).isTrue();
    }

    private record AttemptFixture(java.util.UUID centreId, java.util.UUID attemptId) {}

    /** Builds one centre with a published instance, one attempt and one answer. */
    private AttemptFixture centreWithOneAnswer(
            String name,
            String joinCode,
            ExamMode mode,
            QuestionType type,
            GradeSubject gradeSubject,
            Topic topic)
            throws Exception {
        Centre centre = new Centre();
        centre.setName(name);
        centre = centreRepository.save(centre);

        UserAccount teacher = new UserAccount();
        teacher.setCentre(centre);
        teacher.setRole(UserRole.TEACHER);
        teacher.setEmail("teacher@" + name + ".example.com");
        teacher = userAccountRepository.save(teacher);

        UserAccount student = new UserAccount();
        student.setCentre(centre);
        student.setRole(UserRole.STUDENT);
        student = userAccountRepository.save(student);

        Question question = new Question();
        question.setCentre(centre);
        question.setTopic(topic);
        question.setQuestionType(type);
        question.setPayload(objectMapper.readTree("{\"prompt\":\"q\"}"));
        question.setCorrectRef("options[0]");
        question = questionRepository.save(question);

        ExamTemplate template = new ExamTemplate();
        template.setCentre(centre);
        template.setGrade(gradeSubject.getGrade());
        template.setSubject(gradeSubject.getSubject());
        template.setTopic(topic);
        template.setExamMode(mode);
        template.setTemplateName(name + " Template");
        template.setRules(objectMapper.readTree("{}"));
        template = examTemplateRepository.save(template);

        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName(name + " Batch");
        batch = batchRepository.save(batch);

        ExamInstance instance = new ExamInstance();
        instance.setCentreId(centre.getCentreId());
        instance.setExamTemplate(template);
        instance.setBatch(batch);
        instance.setPublishedBy(teacher);
        instance.setJoinCode(joinCode);
        instance = examInstanceRepository.save(instance);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setCentreId(centre.getCentreId());
        attempt.setExamInstance(instance);
        attempt.setUser(student);
        attempt.setStartedAt(Instant.parse("2026-10-28T15:05:00Z"));
        attempt = examAttemptRepository.save(attempt);

        QuestionAttempt answer = new QuestionAttempt();
        answer.setCentreId(centre.getCentreId());
        answer.setAttempt(attempt);
        answer.setQuestion(question);
        answer.setQuestionVersion(1);
        answer.setAnswerPayload(objectMapper.readTree("{\"selected\":\"options[0]\"}"));
        questionAttemptRepository.save(answer);

        return new AttemptFixture(centre.getCentreId(), attempt.getAttemptId());
    }

    @Test
    void answersAreInvisibleAcrossCentres() throws Exception {
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
        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Fractions");
        topic = topicRepository.save(topic);
        QuestionType mcq = new QuestionType();
        mcq.setCode("MCQ");
        mcq.setName("Multiple Choice");
        mcq = questionTypeRepository.save(mcq);
        ExamMode mode = new ExamMode();
        mode.setTestType(ExamModeType.quiz);
        mode.setTitle("Quiz");
        mode = examModeRepository.save(mode);

        AttemptFixture alpha = centreWithOneAnswer("Alpha", "ALPHA-1", mode, mcq, gradeSubject, topic);
        AttemptFixture beta = centreWithOneAnswer("Beta", "BETA-1", mode, mcq, gradeSubject, topic);

        entityManager.flush();
        entityManager.clear();

        // Each centre reads its own answers…
        assertThat(
                        questionAttemptRepository.findByCentreIdAndAttemptId(
                                alpha.centreId(), alpha.attemptId()))
                .hasSize(1);
        assertThat(
                        questionAttemptRepository.findByCentreIdAndAttemptId(
                                beta.centreId(), beta.attemptId()))
                .hasSize(1);
        // …but a REAL foreign attempt id under the caller's centre returns
        // nothing — the centre predicate, not the id's existence, gates reads.
        assertThat(
                        questionAttemptRepository.findByCentreIdAndAttemptId(
                                alpha.centreId(), beta.attemptId()))
                .isEmpty();
        assertThat(
                        questionAttemptRepository.findByCentreIdAndAttemptId(
                                beta.centreId(), alpha.attemptId()))
                .isEmpty();
    }

    @Test
    void topicStatsBadgesAndXpRoundTrip() throws Exception {
        Centre centre = new Centre();
        centre.setName("Alpha Tutoring");
        centre = centreRepository.save(centre);

        UserAccount student = new UserAccount();
        student.setCentre(centre);
        student.setRole(UserRole.STUDENT);
        student.setDisplayName("Simran");
        student = userAccountRepository.save(student);

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
        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Fractions");
        topic = topicRepository.save(topic);

        UserTopicStat stat = new UserTopicStat();
        stat.setCentreId(centre.getCentreId());
        stat.setUser(student);
        stat.setTopic(topic);
        stat.setAttempts(10);
        stat.setCorrect(7);
        stat.setAvgTimeMs(4200);
        stat.setXpTotal(70);
        stat.setLastActivityAt(Instant.parse("2026-10-28T15:30:00Z"));
        userTopicStatRepository.save(stat);

        Badge badge = new Badge();
        badge.setCode("FRACTION_STAR");
        badge.setName("Fraction Star");
        badge.setRule(objectMapper.readTree("{\"topic\":\"Fractions\",\"minCorrect\":5}"));
        badge = badgeRepository.save(badge);

        UserBadges userBadge = new UserBadges();
        userBadge.setCentreId(centre.getCentreId());
        userBadge.setUser(student);
        userBadge.setBadge(badge);
        userBadgesRepository.save(userBadge);

        UserXpLedger xp = new UserXpLedger();
        xp.setCentreId(centre.getCentreId());
        xp.setUser(student);
        xp.setSourceType(XpSourceType.exam);
        xp.setSourceId(badge.getBadgeId());
        xp.setXpDelta(25);
        userXpLedgerRepository.save(xp);

        entityManager.flush();
        entityManager.clear();

        UserTopicStat reloaded =
                userTopicStatRepository
                        .findByCentreIdAndUserIdAndTopicId(
                                centre.getCentreId(), student.getUserId(), topic.getTopicId())
                        .orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(10);
        assertThat(reloaded.getCorrect()).isEqualTo(7);
        assertThat(
                        userTopicStatRepository.findByCentreIdAndUserIdAndTopicId(
                                centre.getCentreId(), student.getUserId(), badge.getBadgeId()))
                .isEmpty();
        assertThat(badgeRepository.findByCode("FRACTION_STAR")).isPresent();
        assertThat(badgeRepository.findByCode("NOPE")).isEmpty();
        assertThat(userBadgesRepository.findByCentreIdAndUserId(centre.getCentreId(), student.getUserId()))
                .hasSize(1);
        assertThat(userXpLedgerRepository.findByCentreIdAndUserId(centre.getCentreId(), student.getUserId()))
                .extracting(UserXpLedger::getXpDelta)
                .containsExactly(25);
        assertThat(
                        userXpLedgerRepository
                                .findByCentreIdAndUserId(centre.getCentreId(), student.getUserId())
                                .get(0)
                                .getSourceType())
                .isEqualTo(XpSourceType.exam);
    }
}
