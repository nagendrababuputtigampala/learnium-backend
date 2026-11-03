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

@Transactional
class TestPublishingFlowTest extends PublishingFixtures {
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
        var wrongRole = member(batch, colleague); wrongRole.setMemberRole(MemberRole.STUDENT); memberships.saveAndFlush(wrongRole);
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
        var mixedPractice = definition(); mixedPractice.put("kind", "FLASHCARD_PRACTICE"); mixedPractice.put("items", List.of(item(mcq, 0))); invalid.add(mixedPractice);
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
    @Test void allRoutesRequireStaffAndAdminsCanPublishOtherTeachersDefinitions() throws Exception {
        String id = create().path("id").asText();
        var requests = List.of(get("/api/v1/tests"), post("/api/v1/tests"), get("/api/v1/tests/" + id), put("/api/v1/tests/" + id), delete("/api/v1/tests/" + id), post("/api/v1/tests/" + id + "/publish"),
                get("/api/v1/instances"), get("/api/v1/instances/" + id), post("/api/v1/instances/" + id + "/close"), post("/api/v1/instances/" + id + "/regenerate-code"), post("/api/v1/instances/" + id + "/students"));
        for (var request : requests) {
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(request.with(as(student)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/tests/" + id + "/publish").with(as(admin)).contentType("application/json").content(body(publishBody())))
                .andExpect(status().isCreated());
        create(definition(), colleague); create(definition(), admin);
        mvc.perform(get("/api/v1/tests").with(as(colleague))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/tests").with(as(foreign))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/tests").with(as(admin))).andExpect(jsonPath("$.length()").value(3));
    }

    @Test void rejectsOtherPairingsAndSupportsZeroMarkPracticeAndExpiredWindows() throws Exception {
        var grade = new Grade(); grade.setCode("P9"); grade.setName("Publish Grade 9"); grades.save(grade);
        var otherPairing = new GradeSubject(); otherPairing.setGrade(grade); otherPairing.setSubject(pairing.getSubject()); pairings.save(otherPairing);
        var otherTopic = new Topic(); otherTopic.setGradeSubject(otherPairing); otherTopic.setTopicName("Other topic"); topics.save(otherTopic);
        mcq.setTopic(otherTopic); questions.saveAndFlush(mcq);
        mvc.perform(post("/api/v1/tests").with(as(owner)).contentType("application/json").content(body(definition()))).andExpect(status().isBadRequest());
        mcq.setTopic(topic); questions.saveAndFlush(mcq);
        var unknown = new QuestionType(); unknown.setCode("UNKNOWN"); unknown.setName("Unknown"); types.save(unknown);
        var originalType = mcq.getQuestionType(); mcq.setQuestionType(unknown); questions.saveAndFlush(mcq);
        mvc.perform(post("/api/v1/tests").with(as(owner)).contentType("application/json").content(body(definition()))).andExpect(status().isBadRequest());
        mcq.setQuestionType(originalType); questions.saveAndFlush(mcq);
        var practice = definition(); practice.put("kind", "FLASHCARD_PRACTICE"); practice.put("items", List.of(item(flashcard, 0)));
        var saved = create(practice, owner);
        var window = publishBody(); window.put("opensAt", Instant.now().minusSeconds(7200).toString()); window.put("closesAt", Instant.now().minusSeconds(3600).toString());
        var expired = publish(saved.path("id").asText(), window);
        assertThat(expired.path("status").asText()).isEqualTo("CLOSED");
        mvc.perform(post("/api/v1/instances/" + expired.path("id").asText() + "/students").with(as(owner)).contentType("application/json").content(body(Map.of("userId", student.getUserId())))).andExpect(status().isBadRequest());
        var expiredRow = instances.findById(UUID.fromString(expired.path("id").asText())).orElseThrow();
        expiredRow.setStatus(ExamInstanceStatus.OPEN); instances.saveAndFlush(expiredRow);
        mvc.perform(get("/api/v1/instances/" + expired.path("id").asText()).with(as(owner))).andExpect(jsonPath("$.status").value("CLOSED"));
        var nullItem = publishBody(); nullItem.put("items", Collections.singletonList(null));
        mvc.perform(post("/api/v1/tests/" + saved.path("id").asText() + "/publish").with(as(owner)).contentType("application/json").content(body(nullItem))).andExpect(status().isBadRequest());
        batch.setGradeSubject(otherPairing); batches.saveAndFlush(batch);
        mvc.perform(post("/api/v1/tests/" + saved.path("id").asText() + "/publish").with(as(owner)).contentType("application/json").content(body(publishBody()))).andExpect(status().isBadRequest());
    }

}
