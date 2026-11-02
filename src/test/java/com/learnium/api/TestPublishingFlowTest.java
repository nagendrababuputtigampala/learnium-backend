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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TestPublishingFlowTest {
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
    Centre centre(String name) { var c = new Centre(); c.setName(name); return centres.save(c); }
    UserAccount user(Centre c, UserRole role) {
        var u = new UserAccount(); u.setCentre(c); u.setRole(role); u.setDisplayName(role + " " + UUID.randomUUID());
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
                ? LearniumPrincipal.student(user.getUserId(), user.getCentre().getCentreId(), batch.getBatchId(), UUID.randomUUID())
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
    @Test void ownedDefinitionsRoundTripDecimalMarksAndDeleteWhenNeverPublished() throws Exception {
        var created = create(); String id = created.path("id").asText();
        assertThat(created.path("createdBy").asText()).isEqualTo(owner.getUserId().toString());
        assertThat(created.path("totalMarks").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(created.path("items").get(0).path("marks").decimalValue()).isEqualByComparingTo("2.50");
        mvc.perform(get("/api/v1/tests").with(as(colleague))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/tests").with(as(owner))).andExpect(jsonPath("$[0].questionCount").value(2)).andExpect(jsonPath("$[0].items").isEmpty());
        mvc.perform(get("/api/v1/tests").with(as(admin))).andExpect(jsonPath("$.length()").value(1));
        for (UserAccount actor : List.of(colleague, foreign, student)) {
            int expected = actor == student ? 403 : 404;
            mvc.perform(get("/api/v1/tests/" + id).with(as(actor))).andExpect(status().is(expected));
            mvc.perform(put("/api/v1/tests/" + id).with(as(actor)).contentType("application/json").content(body(definition()))).andExpect(status().is(expected));
            mvc.perform(delete("/api/v1/tests/" + id).with(as(actor))).andExpect(status().is(expected));
            mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(actor)).contentType("application/json").content(body(publishBody()))).andExpect(status().is(expected));
        }
        mvc.perform(get("/api/v1/tests/" + id).with(as(admin))).andExpect(status().isOk());
        var revised = definition(); revised.put("name", "Revised"); revised.put("items", List.of(item(numeric, 0.75)));
        mvc.perform(put("/api/v1/tests/" + id).with(as(admin)).contentType("application/json").content(body(revised)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Revised")).andExpect(jsonPath("$.questionCount").value(1));
        mvc.perform(delete("/api/v1/tests/" + id).with(as(admin))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tests/" + id).with(as(owner))).andExpect(status().isNotFound());
    }

    @Test void publishingFreezesContentOrderMarksMetadataAndActiveRoster() throws Exception {
        UserAccount inactive = user(centre, UserRole.STUDENT); inactive.setStatus(UserStatus.DISABLED); users.save(inactive); member(batch, inactive);
        var created = create(); String id = created.path("id").asText();
        var overrides = publishBody(); overrides.put("itemMarks", Map.of(mcq.getQuestionId(), 4.25));
        overrides.put("timeLimitSeconds", 1200); overrides.put("maxAttempts", 3); overrides.put("answerReleasePolicy", "ON_SUBMIT"); overrides.put("cameraMode", "ON"); overrides.put("integrityEnabled", true);
        var first = publish(id, overrides); String firstId = first.path("id").asText();
        assertThat(first.path("items").get(0).path("marks").decimalValue()).isEqualByComparingTo("4.25");
        assertThat(first.path("students")).hasSize(1); assertThat(first.path("students").get(0).path("userId").asText()).isEqualTo(student.getUserId().toString());
        assertThat(first.path("joinCode").asText()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
        assertThat(first.path("maxAttempts").asInt()).isEqualTo(3); assertThat(first.path("integrityEnabled").asBoolean()).isTrue();
        var updated = definition(); updated.put("name", "Updated definition"); updated.put("items", List.of(item(numeric, 0.75), item(mcq, 2.5)));
        mvc.perform(put("/api/v1/tests/" + id).with(as(owner)).contentType("application/json").content(body(updated))).andExpect(status().isOk());
        var second = publish(id, publishBody());
        assertThat(second.path("name").asText()).isEqualTo("Updated definition");
        assertThat(second.path("items").get(0).path("questionId").asText()).isEqualTo(numeric.getQuestionId().toString());
        assertThat(second.path("maxAttempts").asInt()).isEqualTo(1); assertThat(second.path("answerReleasePolicy").asText()).isEqualTo("AFTER_CLOSE");
        assertThat(second.path("cameraMode").asText()).isEqualTo("OFF");
        var reloaded = json(mvc.perform(get("/api/v1/instances/" + firstId).with(as(owner))).andExpect(status().isOk()).andReturn());
        assertThat(reloaded).isEqualTo(first);
        mvc.perform(delete("/api/v1/tests/" + id).with(as(owner))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-TST-002"));
        mvc.perform(get("/api/v1/tests").with(as(owner))).andExpect(jsonPath("$[0].publishedInstanceCount").value(2));
        mvc.perform(get("/api/v1/instances").with(as(owner))).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void finalReviewChangesOnlyThePublishAndPracticeIsUnscored() throws Exception {
        var created = create(); var publish = publishBody();
        publish.put("items", List.of(item(numeric, 0.75), item(mcq, 2.5)));
        var instance = publish(created.path("id").asText(), publish);
        assertThat(instance.path("items").get(0).path("questionId").asText()).isEqualTo(numeric.getQuestionId().toString());
        mvc.perform(get("/api/v1/tests/" + created.path("id").asText()).with(as(owner)))
                .andExpect(jsonPath("$.items[0].questionId").value(mcq.getQuestionId().toString()));
        var practice = definition(); practice.put("kind", "FLASHCARD_PRACTICE"); practice.put("items", List.of(item(flashcard, null)));
        var saved = create(practice, owner);
        assertThat(saved.path("totalMarks").decimalValue()).isEqualByComparingTo("0");
        var window = publishBody(); window.put("opensAt", Instant.now().plusSeconds(3600).toString()); window.put("answerReleasePolicy", "NEVER");
        var scheduled = publish(saved.path("id").asText(), window);
        assertThat(scheduled.path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(scheduled.path("items").get(0).path("marks").decimalValue()).isZero();
    }

    @Test void managementUsesCurrentBatchAssignmentAndLateAddsAreIdempotent() throws Exception {
        String id = publish(create().path("id").asText(), publishBody()).path("id").asText();
        for (UserAccount actor : List.of(colleague, foreign, student)) {
            int expected = actor == student ? 403 : 404;
            mvc.perform(get("/api/v1/instances/" + id).with(as(actor))).andExpect(status().is(expected));
            for (String suffix : List.of("regenerate-code", "close", "students")) {
                mvc.perform(post("/api/v1/instances/" + id + "/" + suffix).with(as(actor)).contentType("application/json").content(body(Map.of("userId", student.getUserId())))).andExpect(status().is(expected));
            }
        }
        mvc.perform(get("/api/v1/instances").with(as(colleague))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/instances?batchId=" + batch.getBatchId()).with(as(colleague))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/instances?batchId=" + batch.getBatchId()).with(as(admin))).andExpect(jsonPath("$.length()").value(1));
        teacherAssignment.setStatus(MembershipStatus.INACTIVE); memberships.saveAndFlush(teacherAssignment); member(batch, colleague);
        mvc.perform(get("/api/v1/instances/" + id).with(as(owner))).andExpect(status().isNotFound());
        var detail = json(mvc.perform(get("/api/v1/instances/" + id).with(as(colleague))).andExpect(status().isOk()).andReturn());
        String oldCode = detail.path("joinCode").asText();
        var regenerated = json(mvc.perform(post("/api/v1/instances/" + id + "/regenerate-code").with(as(colleague))).andExpect(status().isOk()).andReturn());
        assertThat(regenerated.path("joinCode").asText()).isNotEqualTo(oldCode);
        assertThat(instances.findByJoinCode(oldCode)).isEmpty();
        UserAccount late = user(centre, UserRole.STUDENT); member(batch, late);
        for (int repeat = 0; repeat < 2; repeat++) mvc.perform(post("/api/v1/instances/" + id + "/students").with(as(colleague)).contentType("application/json").content(body(Map.of("userId", late.getUserId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(2));
        mvc.perform(post("/api/v1/instances/" + id + "/students").with(as(admin)).contentType("application/json").content(body(Map.of("userId", UUID.randomUUID())))).andExpect(status().isBadRequest());
        for (int repeat = 0; repeat < 2; repeat++) mvc.perform(post("/api/v1/instances/" + id + "/close").with(as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.closedAt").isNotEmpty());
        mvc.perform(post("/api/v1/instances/" + id + "/students").with(as(admin)).contentType("application/json").content(body(Map.of("userId", late.getUserId()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-TST-003"));
    }

    @Test void validatesAllDefinitionDimensions() throws Exception {
        List<Map<String,Object>> invalid = new ArrayList<>();
        for (Object name : Arrays.asList(null, " ", "x".repeat(121))) { var d = definition(); d.put("name", name); invalid.add(d); }
        for (Object kind : Arrays.asList(null, "MIXED")) { var d = definition(); d.put("kind", kind); invalid.add(d); }
        for (Object pairingId : Arrays.asList(null, UUID.randomUUID())) { var d = definition(); d.put("gradeSubjectId", pairingId); invalid.add(d); }
        for (Object items : Arrays.asList(null, List.of(), Collections.singletonList(null), List.of(item(mcq, null)), List.of(item(mcq, 0)), List.of(item(mcq, -1)), List.of(item(mcq, 0.001)), List.of(item(mcq, 10000)), List.of(item(flashcard, 1)), List.of(item(mcq, 1), item(mcq, 1)))) {
            var d = definition(); d.put("items", items); invalid.add(d);
        }
        var tooMany = definition(); tooMany.put("items", Collections.nCopies(201, item(mcq, 1))); invalid.add(tooMany);
        var missing = definition(); missing.put("items", List.of(Map.of("marks", 1))); invalid.add(missing);
        var practice = definition(); practice.put("kind", "FLASHCARD_PRACTICE"); practice.put("items", List.of(item(flashcard, 1))); invalid.add(practice);
        for (var d : invalid) mvc.perform(post("/api/v1/tests").with(as(owner)).contentType("application/json").content(body(d)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-TST-001")).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        mcq.setStatus(QuestionStatus.DRAFT); questions.saveAndFlush(mcq);
        mvc.perform(post("/api/v1/tests").with(as(owner)).contentType("application/json").content(body(definition()))).andExpect(status().isBadRequest());
        mcq.setStatus(QuestionStatus.APPROVED); mcq.setCentre(otherCentre); questions.saveAndFlush(mcq);
        mvc.perform(post("/api/v1/tests").with(as(owner)).contentType("application/json").content(body(definition()))).andExpect(status().isBadRequest());
    }

    @Test void validatesPublishSettingsAndRollsBackEmptyRosterPublish() throws Exception {
        String id = create().path("id").asText(); long before = instances.count();
        Map<String,Object> badValues = new LinkedHashMap<>(); badValues.put("closesAt", Instant.now().minusSeconds(100).toString());
        badValues.put("timeLimitSeconds", 0); badValues.put("maxAttempts", 0); badValues.put("answerReleasePolicy", "WRONG"); badValues.put("cameraMode", "WRONG");
        for (var bad : badValues.entrySet()) { var d = publishBody(); d.put(bad.getKey(), bad.getValue());
            mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(d))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-TST-003"));
        }
        var absentClose = publishBody(); absentClose.remove("closesAt");
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(absentClose))).andExpect(status().isBadRequest());
        var noBatch = publishBody(); noBatch.remove("batchId");
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(noBatch))).andExpect(status().isBadRequest());
        var unknownBatch = publishBody(); unknownBatch.put("batchId", UUID.randomUUID());
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(unknownBatch))).andExpect(status().isNotFound());
        var unknownOverride = publishBody(); unknownOverride.put("itemMarks", Map.of(UUID.randomUUID(), 1));
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(unknownOverride))).andExpect(status().isBadRequest());
        batch.setActive(false); batches.saveAndFlush(batch);
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(publishBody()))).andExpect(status().isBadRequest());
        batch.setActive(true); batches.saveAndFlush(batch);
        for (var member : memberships.findByCentreIdAndBatchIdAndMemberRoleAndStatus(centre.getCentreId(), batch.getBatchId(), MemberRole.STUDENT, MembershipStatus.ACTIVE)) { member.setStatus(MembershipStatus.INACTIVE); memberships.save(member); }
        memberships.flush();
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(owner)).contentType("application/json").content(body(publishBody())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.batchId").isNotEmpty());
        assertThat(instances.count()).isEqualTo(before);
    }
}
