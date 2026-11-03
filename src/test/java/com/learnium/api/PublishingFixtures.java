package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.learnium.generation.*;
import com.learnium.model.common.*;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.model.master.*;
import com.learnium.model.user.*;
import com.learnium.repository.content.*;
import com.learnium.repository.exam.*;
import com.learnium.repository.master.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
abstract class PublishingFixtures {
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired CentreRepository centres;
    @Autowired UserAccountRepository users;
    @Autowired GradeRepository grades;
    @Autowired SubjectRepository subjects;
    @Autowired GradeSubjectRepository pairings;
    @Autowired TopicRepository topics;
    @Autowired QuestionTypeRepository types;
    @Autowired QuestionRepository questions;
    @Autowired ExamModeRepository modes;
    @Autowired BatchRepository batches;
    @Autowired BatchMembershipRepository memberships;
    @Autowired ExamTemplateRepository templates;
    @Autowired ExamInstanceRepository instances;
    @Autowired ExamInstanceQuestionRepository frozen;
    @Autowired ExamInstanceStudentRepository assigned;
    Centre centre, otherCentre;
    UserAccount owner, admin, colleague, foreign, student;
    GradeSubject pairing;
    Topic topic;
    Batch batch;
    Question mcq, numeric, flashcard, written;
    BatchMembership teacherAssignment;

    @BeforeEach void setup() throws Exception {
        centre = centre("Publish centre"); otherCentre = centre("Foreign centre");
        owner = user(centre, UserRole.TEACHER); admin = user(centre, UserRole.CENTRE_ADMIN);
        colleague = user(centre, UserRole.TEACHER); foreign = user(otherCentre, UserRole.TEACHER);
        student = user(centre, UserRole.STUDENT);
        Grade grade = new Grade(); grade.setCode("P8"); grade.setName("Publish Grade 8"); grades.save(grade);
        Subject subject = new Subject(); subject.setSubjectName("Publish Mathematics"); subjects.save(subject);
        pairing = new GradeSubject(); pairing.setGrade(grade); pairing.setSubject(subject); pairings.save(pairing);
        topic = new Topic(); topic.setGradeSubject(pairing); topic.setTopicName("Fractions"); topics.save(topic);
        for (ExamModeType type : List.of(ExamModeType.quiz, ExamModeType.flashcards)) {
            if (modes.findByTestType(type).isEmpty()) { var mode = new ExamMode(); mode.setTestType(type); mode.setTitle(type.name()); modes.save(mode); }
        }
        batch = new Batch(); batch.setCentre(centre); batch.setName("Batch 8"); batch.setGradeSubject(pairing); batches.save(batch);
        teacherAssignment = member(batch, owner); member(batch, student);
        mcq = question("MCQ"); numeric = question("NUMERIC"); flashcard = question("FLASHCARD"); written = question("WRITTEN");
        questions.flush(); memberships.flush();
    }
    Centre centre(String name) { var c = new Centre(); c.setName(name); c.setCode("t" + UUID.randomUUID().toString().substring(0, 8)); return centres.save(c); }
    UserAccount user(Centre c, UserRole role) {
        var u = new UserAccount(); u.setCentre(c); u.setRole(role); u.setDisplayName(role + " " + UUID.randomUUID());
        if (role == UserRole.STUDENT) u.setUsername("s" + UUID.randomUUID().toString().substring(0, 8));
        return users.save(u);
    }
    BatchMembership member(Batch batch, UserAccount user) {
        var m = new BatchMembership(); m.setCentreId(batch.getCentre().getCentreId()); m.setBatch(batch); m.setUser(user);
        m.setMemberRole(user.getRole() == UserRole.STUDENT ? MemberRole.STUDENT : MemberRole.TEACHER);
        return memberships.saveAndFlush(m);
    }
    Question question(String type) throws Exception {
        var qt = types.findByCode(type).orElseGet(() -> { var t = new QuestionType(); t.setCode(type); t.setName(type); return types.save(t); });
        String raw = switch (type) {
            case "MCQ" -> "{\"stem\":\"Which fraction?\",\"options\":[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"},{\"text\":\"d\"}],\"correctIndex\":0}";
            case "NUMERIC" -> "{\"stem\":\"Calculate\",\"answerValue\":2,\"workedSolution\":\"Work\"}";
            case "FLASHCARD" -> "{\"front\":\"Half\",\"back\":\"Two quarters\"}";
            default -> "{\"stem\":\"Explain halves\",\"modelAnswer\":\"Two equal parts\",\"rubric\":[{\"criterion\":\"A\",\"maxScore\":1},{\"criterion\":\"B\",\"maxScore\":1},{\"criterion\":\"C\",\"maxScore\":1}]}";
        };
        var valid = new DraftQuestionFactory().validateForReview(GeneratedQuestionType.valueOf(type), JSON.readTree(raw)).draft();
        var q = new Question(); q.setCentre(centre); q.setCreatedBy(owner.getUserId()); q.setTopic(topic); q.setQuestionType(qt);
        q.setPayload(valid.payload()); q.setCorrectRef(valid.correctRef()); q.setStatus(QuestionStatus.APPROVED);
        return questions.saveAndFlush(q);
    }
    RequestPostProcessor as(UserAccount user) {
        LearniumPrincipal principal = user.getRole() == UserRole.STUDENT
                ? LearniumPrincipal.student(user.getUserId(), user.getCentre().getCentreId())
                : LearniumPrincipal.staff(user.getUserId(), user.getCentre().getCentreId(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
    }
    JsonNode json(MvcResult result) throws Exception { return JSON.readTree(result.getResponse().getContentAsString()); }
    String body(Object value) throws Exception { return JSON.writeValueAsString(value); }
    Map<String, Object> item(Question q, Object marks) { var map = new HashMap<String,Object>(); map.put("questionId", q.getQuestionId()); map.put("marks", marks); return map; }
    Map<String, Object> definition() { return new HashMap<>(Map.of("name", "Fractions check", "gradeSubjectId", pairing.getGradeSubjectId(), "kind", "SCORED_TEST", "items", List.of(item(mcq, 2.5), item(written, 0.75)))); }
    JsonNode create() throws Exception { return create(definition(), owner); }
    JsonNode create(Object definition, UserAccount actor) throws Exception {
        return json(mvc.perform(post("/api/v1/tests").with(as(actor)).contentType("application/json").content(body(definition))).andExpect(status().isCreated()).andReturn());
    }
    Map<String, Object> publishBody() { return new HashMap<>(Map.of("batchId", batch.getBatchId(), "closesAt", Instant.now().plusSeconds(7200).toString())); }
    JsonNode publish(String id, Object body) throws Exception {
        return json(mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(body))).andExpect(status().isCreated()).andReturn());
    }
}
