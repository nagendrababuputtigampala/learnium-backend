package com.learnium.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.model.common.ExamModeType;
import com.learnium.model.content.Question;
import com.learnium.model.content.Topic;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamInstance;
import com.learnium.model.exam.ExamTemplate;
import com.learnium.model.exam.QuestionAttempt;
import com.learnium.model.master.ExamMode;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.ExamInstanceRepository;
import com.learnium.repository.exam.ExamTemplateRepository;
import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.repository.master.ExamModeRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the V5 database-level tenancy invariants on the real migrated schema:
 * cross-centre links cannot be stored at all (composite FKs), template
 * uniqueness is per centre, and exam modes are unique per test type. Each test
 * rolls back, so the shared master data seeded by V2/V3 is reused read-only.
 */
@SpringBootTest
@ActiveProfiles("migration")
@Transactional
class TenancyInvariantTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private BatchMembershipRepository membershipRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private QuestionTypeRepository questionTypeRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private ExamModeRepository examModeRepository;
    @Autowired private ExamTemplateRepository examTemplateRepository;
    @Autowired private ExamInstanceRepository examInstanceRepository;
    @Autowired private ExamAttemptRepository examAttemptRepository;
    @Autowired private QuestionAttemptRepository questionAttemptRepository;

    private record Fixture(
            Centre centreA,
            Centre centreB,
            UserAccount teacherA,
            UserAccount studentA,
            UserAccount studentB,
            Batch batchA,
            Batch batchB,
            GradeSubject gradeSubject,
            Topic topic) {}

    private UserAccount saveUser(Centre centre, UserRole role, String displayName) {
        UserAccount user = new UserAccount();
        user.setCentre(centre);
        user.setRole(role);
        user.setDisplayName(displayName);
        return userAccountRepository.save(user);
    }

    private Batch saveBatch(Centre centre, GradeSubject gradeSubject, String name) {
        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName(name);
        return batchRepository.save(batch);
    }

    private Fixture fixture() {
        Centre centreA = new Centre();
        centreA.setName("Invariant Alpha");
        centreA = centreRepository.save(centreA);
        Centre centreB = new Centre();
        centreB.setName("Invariant Beta");
        centreB = centreRepository.save(centreB);

        GradeSubject gradeSubject =
                gradeSubjectRepository
                        .findByGradeIdAndSubjectId(
                                gradeRepository.findByCode("G8").orElseThrow().getGradeId(),
                                subjectRepository
                                        .findBySubjectName("Mathematics")
                                        .orElseThrow()
                                        .getSubjectId())
                        .orElseThrow();

        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Invariant Fractions");
        topic = topicRepository.save(topic);

        return new Fixture(
                centreA,
                centreB,
                saveUser(centreA, UserRole.TEACHER, "Teacher A"),
                saveUser(centreA, UserRole.STUDENT, "Student A"),
                saveUser(centreB, UserRole.STUDENT, "Student B"),
                saveBatch(centreA, gradeSubject, "Batch A"),
                saveBatch(centreB, gradeSubject, "Batch B"),
                gradeSubject,
                topic);
    }

    private ExamMode saveQuizMode() {
        ExamMode mode = new ExamMode();
        mode.setTestType(ExamModeType.quiz);
        mode.setTitle("Quiz");
        return examModeRepository.save(mode);
    }

    private ExamTemplate saveTemplate(Centre centre, Fixture f, ExamMode mode, String name)
            throws Exception {
        ExamTemplate template = new ExamTemplate();
        template.setCentre(centre);
        template.setGrade(f.gradeSubject().getGrade());
        template.setSubject(f.gradeSubject().getSubject());
        template.setTopic(f.topic());
        template.setExamMode(mode);
        template.setTemplateName(name);
        template.setRules(objectMapper.readTree("{\"timeLimitMin\":20}"));
        return examTemplateRepository.save(template);
    }

    private Question saveQuestion(Centre centre, Fixture f) throws Exception {
        Question question = new Question();
        question.setCentre(centre);
        question.setTopic(f.topic());
        question.setQuestionType(questionTypeRepository.findByCode("MCQ").orElseThrow());
        question.setPayload(objectMapper.readTree("{\"prompt\":\"1/2 + 1/4?\"}"));
        question.setCorrectRef("options[0]");
        return questionRepository.save(question);
    }

    @Test
    void rosterRowCannotLinkAnotherCentresStudent() {
        Fixture f = fixture();
        BatchMembership bad = new BatchMembership();
        bad.setBatch(f.batchA());
        bad.setCentreId(f.centreA().getCentreId());
        bad.setUser(f.studentB()); // student belongs to centre B
        bad.setMemberRole(MemberRole.STUDENT);
        membershipRepository.save(bad);

        assertThatThrownBy(() -> membershipRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rosterRowCannotLinkAnotherCentresBatch() {
        Fixture f = fixture();
        BatchMembership bad = new BatchMembership();
        bad.setBatch(f.batchB()); // batch belongs to centre B
        bad.setCentreId(f.centreA().getCentreId());
        bad.setUser(f.studentA());
        bad.setMemberRole(MemberRole.STUDENT);
        membershipRepository.save(bad);

        assertThatThrownBy(() -> membershipRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void instanceCannotPublishTemplateToAnotherCentresBatch() throws Exception {
        Fixture f = fixture();
        ExamTemplate templateA = saveTemplate(f.centreA(), f, saveQuizMode(), "Fractions Check");
        examTemplateRepository.flush();

        ExamInstance bad = new ExamInstance();
        bad.setCentreId(f.centreA().getCentreId());
        bad.setExamTemplate(templateA);
        bad.setBatch(f.batchB()); // batch belongs to centre B
        bad.setPublishedBy(f.teacherA());
        bad.setJoinCode("BAD-0001");
        examInstanceRepository.save(bad);

        assertThatThrownBy(() -> examInstanceRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void questionAttemptCannotReferenceAnotherCentresQuestion() throws Exception {
        Fixture f = fixture();
        ExamTemplate templateA = saveTemplate(f.centreA(), f, saveQuizMode(), "Fractions Check");

        ExamInstance instance = new ExamInstance();
        instance.setCentreId(f.centreA().getCentreId());
        instance.setExamTemplate(templateA);
        instance.setBatch(f.batchA());
        instance.setPublishedBy(f.teacherA());
        instance.setJoinCode("INV-0001");
        instance = examInstanceRepository.save(instance);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setCentreId(f.centreA().getCentreId());
        attempt.setExamInstance(instance);
        attempt.setUser(f.studentA());
        attempt.setStartedAt(Instant.parse("2026-10-28T15:05:00Z"));
        attempt = examAttemptRepository.save(attempt);
        examAttemptRepository.flush();

        Question questionB = saveQuestion(f.centreB(), f);
        QuestionAttempt bad = new QuestionAttempt();
        bad.setCentreId(f.centreA().getCentreId());
        bad.setAttempt(attempt);
        bad.setQuestion(questionB); // question belongs to centre B
        bad.setQuestionVersion(1);
        bad.setAnswerPayload(objectMapper.readTree("{\"selected\":\"options[0]\"}"));
        questionAttemptRepository.save(bad);

        assertThatThrownBy(() -> questionAttemptRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void identicalTemplateNameAndVersionCoexistAcrossCentres() throws Exception {
        Fixture f = fixture();
        ExamMode mode = saveQuizMode();
        saveTemplate(f.centreA(), f, mode, "Fractions Check");
        saveTemplate(f.centreB(), f, mode, "Fractions Check");
        examTemplateRepository.flush();

        assertThat(examTemplateRepository.findByCentreId(f.centreA().getCentreId())).hasSize(1);
        assertThat(examTemplateRepository.findByCentreId(f.centreB().getCentreId())).hasSize(1);
    }

    @Test
    void duplicateTemplateWithinOneCentreIsRejected() throws Exception {
        Fixture f = fixture();
        ExamMode mode = saveQuizMode();
        saveTemplate(f.centreA(), f, mode, "Fractions Check");
        examTemplateRepository.flush();

        saveTemplate(f.centreA(), f, mode, "Fractions Check");
        assertThatThrownBy(() -> examTemplateRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondExamModeWithSameTestTypeIsRejected() {
        saveQuizMode();
        examModeRepository.flush();

        ExamMode second = new ExamMode();
        second.setTestType(ExamModeType.quiz);
        second.setTitle("Quiz — alternate title");
        examModeRepository.save(second);

        assertThatThrownBy(() -> examModeRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
