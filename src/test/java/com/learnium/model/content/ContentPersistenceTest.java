package com.learnium.model.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.config.JpaAuditingConfig;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.QuestionType;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ContentPersistenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private QuestionTypeRepository questionTypeRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private TestEntityManager entityManager;

    private record Fixture(Centre centre, UserAccount teacher, GradeSubject gradeSubject, Topic topic) {}

    private Fixture fixture(String centreName) {
        Centre centre = new Centre();
        centre.setName(centreName);
        centre = centreRepository.save(centre);

        UserAccount teacher = new UserAccount();
        teacher.setCentre(centre);
        teacher.setRole(UserRole.TEACHER);
        teacher.setEmail("teacher@" + centreName.hashCode() + ".example.com");
        teacher.setDisplayName("Teacher");
        teacher = userAccountRepository.save(teacher);

        Grade grade = new Grade();
        grade.setCode("G" + (Math.abs(centreName.hashCode()) % 90 + 10));
        grade.setName("Grade");
        grade = gradeRepository.save(grade);

        Subject subject = new Subject();
        subject.setSubjectName("Subject " + centreName);
        subject = subjectRepository.save(subject);

        GradeSubject gradeSubject = new GradeSubject();
        gradeSubject.setGrade(grade);
        gradeSubject.setSubject(subject);
        gradeSubject = gradeSubjectRepository.save(gradeSubject);

        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName("Fractions");
        topic = topicRepository.save(topic);
        return new Fixture(centre, teacher, gradeSubject, topic);
    }

    private QuestionType saveQuestionType(String code, String name) {
        QuestionType type = new QuestionType();
        type.setCode(code);
        type.setName(name);
        return questionTypeRepository.save(type);
    }

    private Question saveQuestion(Fixture f, QuestionType type, QuestionStatus status, String prompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("prompt", prompt);
        payload.putArray("options").add("1/2").add("1/3");
        Question question = new Question();
        question.setCentre(f.centre());
        question.setTopic(f.topic());
        question.setQuestionType(type);
        question.setDifficulty((short) 2);
        question.setPayload(payload);
        question.setCorrectRef("options[0]");
        question.setStatus(status);
        return questionRepository.save(question);
    }

    @Test
    void questionPayloadRoundTripsAsJson() {
        Fixture f = fixture("Alpha");
        QuestionType mcq = saveQuestionType("MCQ", "Multiple Choice");
        Question saved = saveQuestion(f, mcq, QuestionStatus.DRAFT, "What is 1/2 + 1/4?");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(QuestionStatus.DRAFT);

        entityManager.flush();
        entityManager.clear();

        Question reloaded = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(reloaded.getPayload().get("prompt").asText()).isEqualTo("What is 1/2 + 1/4?");
        assertThat(reloaded.getPayload().get("options")).hasSize(2);
        assertThat(reloaded.getCorrectRef()).isEqualTo("options[0]");
        assertThat(reloaded.getMaterial()).isNull();
        assertThat(reloaded.getQuestionType().getCode()).isEqualTo("MCQ");
        assertThat(questionTypeRepository.findByCode("MCQ")).isPresent();
        assertThat(questionTypeRepository.findByCode("ESSAY")).isEmpty();
    }

    @Test
    void centreScopedStatusPageReturnsOnlyThatCentresQuestions() {
        Fixture alpha = fixture("Alpha");
        Fixture beta = fixture("Beta");
        QuestionType mcq = saveQuestionType("MCQ", "Multiple Choice");
        saveQuestion(alpha, mcq, QuestionStatus.DRAFT, "A1");
        saveQuestion(alpha, mcq, QuestionStatus.DRAFT, "A2");
        saveQuestion(alpha, mcq, QuestionStatus.DRAFT, "A3");
        saveQuestion(alpha, mcq, QuestionStatus.APPROVED, "A4");
        saveQuestion(beta, mcq, QuestionStatus.DRAFT, "B1");

        entityManager.flush();
        entityManager.clear();

        Page<Question> page0 =
                questionRepository.findByCentreIdAndStatus(
                        alpha.centre().getCentreId(), QuestionStatus.DRAFT, PageRequest.of(0, 2));
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getContent()).hasSize(2);

        Page<Question> page1 =
                questionRepository.findByCentreIdAndStatus(
                        alpha.centre().getCentreId(), QuestionStatus.DRAFT, PageRequest.of(1, 2));
        assertThat(page1.getContent()).hasSize(1);

        Page<Question> betaDrafts =
                questionRepository.findByCentreIdAndStatus(
                        beta.centre().getCentreId(), QuestionStatus.DRAFT, PageRequest.of(0, 10));
        assertThat(betaDrafts.getTotalElements()).isEqualTo(1);
        assertThat(betaDrafts.getContent().get(0).getPayload().get("prompt").asText()).isEqualTo("B1");
    }

    @Test
    void materialAndTopicFinders() {
        Fixture f = fixture("Alpha");
        QuestionType written = saveQuestionType("WRITTEN", "Written Answer");

        Material material = new Material();
        material.setCentre(f.centre());
        material.setUploadedBy(f.teacher());
        material.setGradeSubject(f.gradeSubject());
        material.setTopic(f.topic());
        material.setTitle("Chapter 4 — Fractions");
        material.setSourceType(MaterialSourceType.PDF);
        material.setStorageKey("materials/ch4.pdf");
        material.setExtractedText("A fraction is part of a whole.");
        material = materialRepository.save(material);

        Question generated = saveQuestion(f, written, QuestionStatus.DRAFT, "Explain fractions.");
        generated.setMaterial(material);
        questionRepository.save(generated);

        entityManager.flush();
        entityManager.clear();

        assertThat(materialRepository.findByCentreId(f.centre().getCentreId()))
                .extracting(Material::getTitle)
                .containsExactly("Chapter 4 — Fractions");
        Material reloaded = materialRepository.findById(material.getMaterialId()).orElseThrow();
        assertThat(reloaded.getSourceType()).isEqualTo(MaterialSourceType.PDF);
        assertThat(reloaded.getExtractedText()).contains("part of a whole");
        Question reloadedQuestion = questionRepository.findById(generated.getQuestionId()).orElseThrow();
        assertThat(reloadedQuestion.getMaterial().getMaterialId()).isEqualTo(material.getMaterialId());

        assertThat(topicRepository.findByGradeSubjectId(f.gradeSubject().getGradeSubjectId()))
                .extracting(Topic::getTopicName)
                .containsExactly("Fractions");
        assertThat(
                        topicRepository.findByGradeSubjectIdAndTopicName(
                                f.gradeSubject().getGradeSubjectId(), "Fractions"))
                .isPresent();
        assertThat(
                        topicRepository.findByGradeSubjectIdAndTopicName(
                                f.gradeSubject().getGradeSubjectId(), "Decimals"))
                .isEmpty();
    }
}
