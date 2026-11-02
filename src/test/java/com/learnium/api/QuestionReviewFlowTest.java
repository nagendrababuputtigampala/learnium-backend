package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.generation.*;
import com.learnium.model.content.*;
import com.learnium.model.master.*;
import com.learnium.model.user.*;
import com.learnium.repository.content.*;
import com.learnium.repository.master.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.model.common.UserStatus;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class QuestionReviewFlowTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired CentreRepository centres;
    @Autowired UserAccountRepository users;
    @Autowired GradeRepository grades;
    @Autowired SubjectRepository subjects;
    @Autowired GradeSubjectRepository pairings;
    @Autowired TopicRepository topics;
    @Autowired QuestionTypeRepository types;
    @Autowired QuestionRepository questions;
    @Autowired MaterialRepository materials;
    @Autowired PasswordEncoder passwords;
    @Autowired com.learnium.question.QuestionReviewService service;
    @Autowired tools.jackson.databind.ObjectMapper webJson;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    Centre centre;
    UserAccount owner, colleague, foreign;
    Topic topic, otherTopic;
    GradeSubject pairing;

    @BeforeEach void setup() {
        centre = AuthFixtures.createCentre(centres, "Review centre");
        owner = staff(centre, "owner"); colleague = staff(centre, "colleague");
        foreign = staff(AuthFixtures.createCentre(centres, "Foreign centre"), "foreign");
        Grade grade = new Grade(); grade.setCode("R8"); grade.setName("Review Grade 8");
        Subject subject = new Subject(); subject.setSubjectName("Review Maths");
        pairing = new GradeSubject(); pairing.setGrade(grades.save(grade)); pairing.setSubject(subjects.save(subject));
        pairing = pairings.save(pairing);
        topic = new Topic(); topic.setGradeSubject(pairing); topic.setTopicName("Fractions"); topic = topics.save(topic);
        otherTopic = new Topic(); otherTopic.setGradeSubject(pairing); otherTopic.setTopicName("Decimals"); otherTopic = topics.save(otherTopic);
    }
    UserAccount staff(Centre centre, String name) {
        return AuthFixtures.createStaff(users, passwords, centre, UserRole.TEACHER,
                name + UUID.randomUUID() + "@review.test", name, "Passw0rd1", UserStatus.ACTIVE);
    }
    RequestPostProcessor as(UserAccount user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                LearniumPrincipal.staff(user.getUserId(), user.getCentre().getCentreId(), UserRole.TEACHER),
                null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }
    RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                LearniumPrincipal.staff(owner.getUserId(), centre.getCentreId(), UserRole.CENTRE_ADMIN),
                null, List.of(new SimpleGrantedAuthority("ROLE_CENTRE_ADMIN"))));
    }
    String content(GeneratedQuestionType type) {
        return switch (type) {
            case MCQ -> "{\"stem\":\"Which is half?\",\"options\":[{\"text\":\"1/2\"},{\"text\":\"1/3\"},{\"text\":\"1/4\"},{\"text\":\"1/5\"}],\"correctIndex\":0}";
            case FILL_BLANK -> "{\"stemWithBlank\":\"Half equals _____ quarters.\",\"answer\":\"two\"}";
            case FLASHCARD -> "{\"front\":\"Half\",\"back\":\"Two quarters\"}";
            case NUMERIC -> "{\"stem\":\"How many quarters in half?\",\"answerValue\":2,\"tolerance\":0,\"unit\":\"\",\"workedSolution\":\"2/4=1/2\"}";
            case WRITTEN -> "{\"stem\":\"Explain halves\",\"modelAnswer\":\"Two equal parts\",\"rubric\":[{\"criterion\":\"Equal\",\"maxScore\":1},{\"criterion\":\"Two\",\"maxScore\":1},{\"criterion\":\"Parts\",\"maxScore\":1}]}";
        };
    }
    Question draft(GeneratedQuestionType type) throws Exception {
        QuestionType qt = new QuestionType(); qt.setCode(type.name()); qt.setName(type.name());
        qt = types.findByCode(type.name()).orElseGet(() -> types.save(qtFor(type)));
        var item = JSON.readTree(content(type)); ((com.fasterxml.jackson.databind.node.ObjectNode)item).put("type", type.name());
        var valid = new DraftQuestionFactory().validateForReview(type, item).draft();
        Question q = new Question(); q.setCentre(centre); q.setTopic(topic); q.setQuestionType(qt);
        q.setCreatedBy(owner.getUserId()); q.setPayload(valid.payload()); q.setCorrectRef(valid.correctRef());
        return questions.saveAndFlush(q);
    }
    QuestionType qtFor(GeneratedQuestionType type) { QuestionType q = new QuestionType(); q.setCode(type.name()); q.setName(type.name()); return q; }
    String body(Question q, String content) {
        return "{\"revision\":"+q.getRevision()+",\"topicId\":\""+otherTopic.getTopicId()+"\",\"difficulty\":3,\"content\":"+content+"}";
    }
    String decision(Question q) { return "{\"revision\":"+q.getRevision()+"}"; }

    @ParameterizedTest @EnumSource(GeneratedQuestionType.class)
    void editsApprovesAndSharesEveryType(GeneratedQuestionType type) throws Exception {
        Question q = draft(type);
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner))
                .contentType("application/json").content(body(q,content(type))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.difficulty").value(3))
                .andExpect(jsonPath("$.topicId").value(otherTopic.getTopicId().toString()))
                .andExpect(jsonPath("$.revision").value(1));
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content(decision(q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(owner.getUserId().toString()))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());
        mvc.perform(get("/api/v1/questions?status=APPROVED").with(as(colleague)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner))
                .contentType("application/json").content(body(q,content(type))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-QST-001"));
    }

    @Test void hidesPrivateDraftsAndOtherCentresButAllowsCentreAdmin() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        mvc.perform(get("/api/v1/questions").with(as(colleague))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/questions/"+q.getQuestionId()).with(as(colleague))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questions/"+q.getQuestionId()).with(as(foreign))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questions/"+UUID.randomUUID()).with(admin())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questions").with(admin())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/reject").with(as(colleague))
                .contentType("application/json").content(decision(q))).andExpect(status().isNotFound());
        q.setActive(false); questions.saveAndFlush(q);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/reject").with(as(owner))
                .contentType("application/json").content(decision(q))).andExpect(status().isNotFound());
    }

    @Test void rejectsBrokenDraftWithoutValidationAndNeverReopensIt() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ); q.setPayload(JSON.createObjectNode()); questions.saveAndFlush(q);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/reject").with(as(owner))
                .contentType("application/json").content(decision(q))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/questions?status=REJECTED").with(as(owner))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/questions?status=REJECTED").with(as(colleague))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(admin())
                .contentType("application/json").content(decision(q))).andExpect(status().isConflict());
    }

    @Test void forksOnlyApprovedQuestionsAndKeepsPdfPrivate() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/forks").with(as(owner))).andExpect(status().isConflict());
        Material material = new Material(); material.setCentre(centre); material.setUploadedBy(owner);
        material.setGradeSubject(pairing); material.setTitle("Private PDF"); material.setSourceType(MaterialSourceType.PASTE);
        q.setMaterial(materials.save(material)); q.setStatus(QuestionStatus.APPROVED); questions.saveAndFlush(q);
        mvc.perform(get("/api/v1/questions/"+q.getQuestionId()).with(as(colleague)))
                .andExpect(jsonPath("$.materialId").isEmpty()).andExpect(jsonPath("$.materialTitle").isEmpty());
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/forks").with(as(colleague)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value(colleague.getUserId().toString()))
                .andExpect(jsonPath("$.sourceQuestionId").value(q.getQuestionId().toString()))
                .andExpect(jsonPath("$.materialId").isEmpty()).andExpect(jsonPath("$.approvedAt").isEmpty());
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.APPROVED);
    }

    @Test void validatesRevisionTypeAndContentWithSafeFieldErrors() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,"{}"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.stem']").isNotEmpty());
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,"[]"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,"{\"type\":\"NUMERIC\"}"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content("{\"revision\":999}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-QST-004"));
        q.setCorrectRef("missing"); questions.saveAndFlush(q);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content(decision(q))).andExpect(status().isUnprocessableContent());
        q.setPayload(JSON.createArrayNode()); questions.saveAndFlush(q);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content(decision(q))).andExpect(status().isUnprocessableContent());
    }

    @Test void limitsTopicsToOriginalPairingAndAcceptsActiveTopicOnly() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        for (UUID target : List.of(UUID.randomUUID(), otherTopic.getTopicId())) {
            otherTopic.setActive(false); topics.saveAndFlush(otherTopic);
            mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                    .content(body(q,content(GeneratedQuestionType.MCQ)).replace(otherTopic.getTopicId().toString(),target.toString())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void preservesRubricIdentityAndRejectsForgedIds() throws Exception {
        Question q = draft(GeneratedQuestionType.WRITTEN);
        var content = q.getPayload().deepCopy();
        var rubric = (com.fasterxml.jackson.databind.node.ArrayNode) content.path("rubric");
        rubric.addObject().put("criterion", "Extra").put("maxScore", 1);
        rubric.addObject().put("criterion", "Another").put("maxScore", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("maxScore", 5);
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,content.toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.rubric[0].id").value("crit-1"))
                .andExpect(jsonPath("$.payload.rubric[3].id").value("crit-4"))
                .andExpect(jsonPath("$.payload.rubric[4].id").value("crit-5"));
        for (String forged : List.of("crit-1", "forged")) {
            var changed = q.getPayload().deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("rubric").get(1)).put("id",forged);
            mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                    .content(body(q,changed.toString()))).andExpect(status().isUnprocessableContent());
        }
    }

    @Test void refusesOtherGradeSubjectAndAllowsMatchingTypeInContent() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        Grade grade = new Grade(); grade.setCode("R9"); grade.setName("Review Grade 9");
        GradeSubject otherPairing = new GradeSubject(); otherPairing.setGrade(grades.save(grade)); otherPairing.setSubject(pairing.getSubject());
        Topic foreignTopic = new Topic(); foreignTopic.setTopicName("Other grade"); foreignTopic.setGradeSubject(pairings.save(otherPairing));
        foreignTopic = topics.saveAndFlush(foreignTopic);
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,content(GeneratedQuestionType.MCQ)).replace(otherTopic.getTopicId().toString(),foreignTopic.getTopicId().toString())))
                .andExpect(status().isBadRequest());
        var content = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.MCQ));
        content.put("type","MCQ");
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,content.toString()))).andExpect(status().isOk());
    }

    @Test void reportsFieldLengthsRubricsAndBadParameterTypes() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        var longContent = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.MCQ));
        longContent.put("stem", "x".repeat(501));
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(q,longContent.toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.stem']").value("The value exceeds the allowed length"));
        Question written = draft(GeneratedQuestionType.WRITTEN);
        var badRubric = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.WRITTEN));
        badRubric.putArray("rubric");
        mvc.perform(put("/api/v1/questions/"+written.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(written,badRubric.toString()))).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/questions?topicId=not-a-uuid").with(as(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/questions/not-a-uuid").with(as(owner))).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousEditsCommitExactlyOneRevision() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        var caller = LearniumPrincipal.staff(owner.getUserId(), centre.getCentreId(), UserRole.TEACHER);
        var edit = new com.learnium.api.dto.QuestionDtos.EditRequest(q.getRevision(), topic.getTopicId(),
                (short) 3, JSON.readValue(content(GeneratedQuestionType.MCQ), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}));
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> task = () -> {
                start.await();
                try { service.edit(caller,q.getQuestionId(),edit); return "OK"; }
                catch (com.learnium.common.error.BusinessException e) { return e.getErrorCode().getCode(); }
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "LRN-QST-004");
            assertThat(questions.findById(q.getQuestionId()).orElseThrow().getRevision()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM learnium.questions WHERE centre_id = ?", centre.getCentreId());
            jdbc.update("DELETE FROM learnium.topics WHERE grade_subject_id = ?", pairing.getGradeSubjectId());
            jdbc.update("DELETE FROM learnium.grade_subjects WHERE grade_subject_id = ?", pairing.getGradeSubjectId());
            jdbc.update("DELETE FROM learnium.grades WHERE grade_id = ?", pairing.getGrade().getGradeId());
            jdbc.update("DELETE FROM learnium.subjects WHERE subject_id = ?", pairing.getSubject().getSubjectId());
            jdbc.update("DELETE FROM learnium.user_account WHERE centre_id IN (?, ?)", centre.getCentreId(),foreign.getCentre().getCentreId());
            jdbc.update("DELETE FROM learnium.centres WHERE centre_id IN (?, ?)", centre.getCentreId(),foreign.getCentre().getCentreId());
        }
    }

    @Test void rejectsStudentRolesAndMissingReviewFields() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        var student = authentication(new UsernamePasswordAuthenticationToken(
                LearniumPrincipal.student(UUID.randomUUID(), centre.getCentreId(), UUID.randomUUID(), UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        mvc.perform(get("/api/v1/questions").with(student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.revision").isNotEmpty());
        mvc.perform(put("/api/v1/questions/"+q.getQuestionId()).with(as(owner))
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void approvalPersistsCanonicalLegacyBlankContent() throws Exception {
        Question q = draft(GeneratedQuestionType.FILL_BLANK);
        q.setPayload(JSON.createObjectNode().put("stem", "One half equals _______ quarters.").put("answer", "two"));
        questions.saveAndFlush(q);
        mvc.perform(post("/api/v1/questions/"+q.getQuestionId()+"/approve").with(as(owner))
                .contentType("application/json").content(decision(q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.payload.stemWithBlank").value("One half equals _____ quarters."))
                .andExpect(jsonPath("$.payload.stem").doesNotExist());
    }

    @Test void givesActionableErrorsForOptionsBlanksAndNumericFields() throws Exception {
        Question mcq = draft(GeneratedQuestionType.MCQ);
        var duplicateOptions = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.MCQ));
        ((com.fasterxml.jackson.databind.node.ObjectNode) duplicateOptions.path("options").get(1)).put("text", "1/2");
        mvc.perform(put("/api/v1/questions/"+mcq.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(mcq,duplicateOptions.toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.options']").value("Use four distinct, non-empty options"));
        Question blank = draft(GeneratedQuestionType.FILL_BLANK);
        var noBlank = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.FILL_BLANK));
        noBlank.put("stemWithBlank", "There is no blank here");
        mvc.perform(put("/api/v1/questions/"+blank.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(blank,noBlank.toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.stemWithBlank']").value("Enter text with exactly one _____ blank"));
        Question numeric = draft(GeneratedQuestionType.NUMERIC);
        var badNumber = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(GeneratedQuestionType.NUMERIC));
        badNumber.put("tolerance", -1);
        mvc.perform(put("/api/v1/questions/"+numeric.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(numeric,badNumber.toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.tolerance']").value("Enter a finite, non-negative tolerance"));
        badNumber.putNull("answerValue");
        mvc.perform(put("/api/v1/questions/"+numeric.getQuestionId()).with(as(owner)).contentType("application/json")
                .content(body(numeric,badNumber.toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors['content.answerValue']").value("Enter a finite numeric answer"));
        assertThat(numeric.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    }

    @Test void supportsBankFiltersAndRejectsInvalidFilters() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ); q.setStatus(QuestionStatus.APPROVED); questions.saveAndFlush(q);
        String url="/api/v1/questions?status=APPROVED&gradeSubjectId="+pairing.getGradeSubjectId()+"&gradeId="+pairing.getGrade().getGradeId()
                +"&subjectId="+pairing.getSubject().getSubjectId()+"&topicId="+topic.getTopicId()+"&type=MCQ&difficulty=1&size=200";
        mvc.perform(get(url).with(as(colleague))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get(url.replace("difficulty=1","difficulty=2")).with(as(colleague))).andExpect(jsonPath("$.totalElements").value(0));
        for (String filter : List.of("status=UNKNOWN","type=UNKNOWN","difficulty=no","difficulty=0","difficulty=6")) {
            mvc.perform(get("/api/v1/questions?"+filter).with(as(owner))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/questions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/questions/"+UUID.randomUUID()+"/forks").with(as(owner))).andExpect(status().isNotFound());
    }
    @Test void authorizationMatrixCoversEveryEndpointAndReviewState() throws Exception {
        var student = authentication(new UsernamePasswordAuthenticationToken(
                LearniumPrincipal.student(UUID.randomUUID(), centre.getCentreId(), UUID.randomUUID(), UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        var actors = List.of(as(owner), admin(), as(colleague), as(foreign), student);
        for (QuestionStatus state : QuestionStatus.values()) {
            for (int actor = 0; actor < actors.size(); actor++) {
                boolean visible = actor < 2 || (actor == 2 && state == QuestionStatus.APPROVED);
                for (String endpoint : List.of("detail", "edit", "approve", "reject", "forks")) {
                    Question q = draft(GeneratedQuestionType.MCQ);
                    q.setStatus(state); questions.saveAndFlush(q);
                    String url = "/api/v1/questions/" + q.getQuestionId();
                    var request = switch (endpoint) {
                        case "detail" -> get(url);
                        case "edit" -> put(url).contentType("application/json").content(body(q, content(GeneratedQuestionType.MCQ)));
                        default -> post(url + "/" + endpoint).contentType("application/json").content(decision(q));
                    };
                    int expected = actor == 4 ? 403 : !visible ? 404 : switch (endpoint) {
                        case "detail" -> 200;
                        case "forks" -> state == QuestionStatus.APPROVED ? 201 : 409;
                        default -> state == QuestionStatus.DRAFT ? 200 : 409;
                    };
                    mvc.perform(request.with(actors.get(actor))).andExpect(status().is(expected));
                }
                var list = mvc.perform(get("/api/v1/questions?status=" + state).with(actors.get(actor)))
                        .andExpect(status().is(actor == 4 ? 403 : 200));
                if (actor == 3 || (actor == 2 && state != QuestionStatus.APPROVED)) {
                    list.andExpect(jsonPath("$.totalElements").value(0));
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(GeneratedQuestionType.class)
    void generationContentDropRulesAreRejectedOnEdit(GeneratedQuestionType type) throws Exception {
        Question q = draft(type);
        var base = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(content(type));
        List<com.fasterxml.jackson.databind.node.ObjectNode> invalid = new ArrayList<>();
        Map<String, Integer> lengths = switch (type) {
            case MCQ -> Map.of("stem", 500);
            case FILL_BLANK -> Map.of("stemWithBlank", 500, "answer", 50);
            case FLASHCARD -> Map.of("front", 500, "back", 2000);
            case NUMERIC -> Map.of("stem", 500, "workedSolution", 4000);
            case WRITTEN -> Map.of("stem", 500, "modelAnswer", 4000);
        };
        lengths.forEach((field, max) -> {
            var missing = base.deepCopy(); missing.remove(field); invalid.add(missing);
            invalid.add(base.deepCopy().put(field, " "));
            invalid.add(base.deepCopy().put(field, 42));
            invalid.add(base.deepCopy().put(field, "x".repeat(max + 1)));
        });
        if (type == GeneratedQuestionType.MCQ) {
            var missing = base.deepCopy(); missing.remove("options"); invalid.add(missing);
            invalid.add(base.deepCopy().put("options", "four"));
            var few = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode) few.path("options")).remove(3); invalid.add(few);
            for (String value : List.of("", "1/2", "x".repeat(201))) {
                var bad = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.path("options").get(1)).put("text", value); invalid.add(bad);
            }
            var noText = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) noText.path("options").get(1)).remove("text"); invalid.add(noText);
            var noKey = base.deepCopy(); noKey.remove("correctIndex"); invalid.add(noKey);
            for (int index : List.of(-1, 4)) invalid.add(base.deepCopy().put("correctIndex", index));
            invalid.add(base.deepCopy().put("correctIndex", 1.5));
            invalid.add(base.deepCopy().put("correctIndex", "0"));
        }
        if (type == GeneratedQuestionType.FILL_BLANK) {
            invalid.add(base.deepCopy().put("stemWithBlank", "No blank"));
            invalid.add(base.deepCopy().put("stemWithBlank", "Two _____ _____ blanks"));
            invalid.add(base.deepCopy().put("stemWithBlank", "Short ____ blank"));
        }
        if (type == GeneratedQuestionType.NUMERIC) {
            var missing = base.deepCopy(); missing.remove("answerValue"); invalid.add(missing);
            invalid.add(base.deepCopy().put("answerValue", "two"));
            invalid.add(base.deepCopy().put("answerValue", 1.2345678901234567).put("tolerance", 0.12345678901234567)); // correct_ref limit
            invalid.add(base.deepCopy().put("tolerance", -1));
            invalid.add(base.deepCopy().put("tolerance", "wide"));
            invalid.add(base.deepCopy().put("unit", "u".repeat(21)));
        }
        if (type == GeneratedQuestionType.WRITTEN) {
            var missing = base.deepCopy(); missing.remove("rubric"); invalid.add(missing);
            invalid.add(base.deepCopy().put("rubric", "criteria"));
            for (int size : List.of(2, 6)) {
                var bad = base.deepCopy(); var rubric = bad.putArray("rubric");
                for (int j = 0; j < size; j++) rubric.addObject().put("criterion", "A").put("maxScore", 1);
                invalid.add(bad);
            }
            for (String text : List.of(" ", "x".repeat(201))) {
                var bad = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.path("rubric").get(0)).put("criterion", text); invalid.add(bad);
            }
            for (int score : List.of(0, -1, 101, 99)) {
                var bad = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.path("rubric").get(0)).put("maxScore", score); invalid.add(bad);
            }
            for (String field : List.of("criterion", "maxScore")) {
                var bad = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.path("rubric").get(0)).remove(field); invalid.add(bad);
            }
            var badScore = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) badScore.path("rubric").get(0)).put("maxScore", "one"); invalid.add(badScore);
            var badEntry = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode) badEntry.path("rubric")).set(0, JSON.getNodeFactory().textNode("A")); invalid.add(badEntry);
            invalid.add(base.deepCopy().put("maxScore", 4));
            invalid.add(base.deepCopy().put("maxScore", "three"));
        }
        var factory = new DraftQuestionFactory();
        for (var bad : invalid) {
            bad.put("type", type.name());
            assertThat(factory.validate(JSON.createArrayNode().add(bad), Map.of(type, 1)).drafts()).isEmpty();
            mvc.perform(put("/api/v1/questions/" + q.getQuestionId()).with(as(owner))
                    .contentType("application/json").content(body(q, bad.toString())))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
    }

    @Test void refusesServerOwnedFieldsAndListsOnlySummaryData() throws Exception {
        Question q = draft(GeneratedQuestionType.MCQ);
        for (String fixed : List.of("\"type\":\"NUMERIC\"", "\"gradeSubjectId\":\"" + UUID.randomUUID() + "\"")) {
            String body = body(q, content(GeneratedQuestionType.MCQ));
            body = body.substring(0, body.length() - 1) + "," + fixed + "}";
            mvc.perform(put("/api/v1/questions/" + q.getQuestionId()).with(as(owner))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        }
        mvc.perform(get("/api/v1/questions").with(as(owner)))
                .andExpect(jsonPath("$.content[0].stemPreview").value("Which is half?"))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist())
                .andExpect(jsonPath("$.content[0].correctRef").doesNotExist());
    }

    @ParameterizedTest @EnumSource(GeneratedQuestionType.class)
    void forkCopiesContentAndLineageWithoutMutatingApprovedOriginal(GeneratedQuestionType type) throws Exception {
        Question q = draft(type);
        mvc.perform(post("/api/v1/questions/" + q.getQuestionId() + "/approve").with(admin())
                .contentType("application/json").content(decision(q)))
                .andExpect(status().isOk());
        var before = JSON.readTree(mvc.perform(get("/api/v1/questions/" + q.getQuestionId()).with(as(owner)))
                .andReturn().getResponse().getContentAsString());
        var fork = JSON.readTree(mvc.perform(post("/api/v1/questions/" + q.getQuestionId() + "/forks").with(as(colleague)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (String field : List.of("payload", "correctRef", "topicId", "difficulty", "type")) {
            assertThat(fork.path(field)).isEqualTo(before.path(field));
        }
        assertThat(fork.path("approvedBy").isNull()).isTrue();
        assertThat(fork.path("approvedAt").isNull()).isTrue();
        assertThat(fork.path("createdBy").asText()).isEqualTo(colleague.getUserId().toString());
        assertThat(fork.path("sourceQuestionId").asText()).isEqualTo(q.getQuestionId().toString());
        var after = JSON.readTree(mvc.perform(get("/api/v1/questions/" + q.getQuestionId()).with(as(owner)))
                .andReturn().getResponse().getContentAsString());
        assertThat(after).isEqualTo(before);
        assertThat(Instant.parse(before.path("approvedAt").asText())).isBeforeOrEqualTo(Instant.now());
    }

}
