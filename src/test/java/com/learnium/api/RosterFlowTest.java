package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.learnium.auth.*;
import com.learnium.common.error.*;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.*;
import com.learnium.model.user.*;
import com.learnium.repository.auth.*;
import com.learnium.repository.master.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import com.learnium.testsupport.AuthFixtures;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RosterFlowTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired CentreRepository centres;
    @Autowired UserAccountRepository users; @Autowired BatchRepository batches;
    @Autowired BatchMembershipRepository memberships; @Autowired GradeRepository grades;
    @Autowired SubjectRepository subjects; @Autowired GradeSubjectRepository pairings;
    @Autowired EmailOutboxRepository outbox; @Autowired AccountTokenRepository tokens;
    @Autowired AccountTokenService accountTokens; @Autowired AccountFlowService accountFlows;
    @Autowired PasswordEncoder passwords;
    Centre centre, foreignCentre; UserAccount teacher, admin, foreignTeacher; Batch batch;

    @BeforeEach void setup() {
        centre = AuthFixtures.createCentre(centres, "Roster Centre"); centre.setCode("rc"); centres.save(centre);
        foreignCentre = AuthFixtures.createCentre(centres, "Foreign Centre"); foreignCentre.setCode("fc"); centres.save(foreignCentre);
        teacher = AuthFixtures.createStaff(users, passwords, centre, UserRole.TEACHER, "teacher@roster.test", "Teacher", "Passw0rd1", UserStatus.ACTIVE);
        admin = AuthFixtures.createStaff(users, passwords, centre, UserRole.CENTRE_ADMIN, "admin@roster.test", "Admin", "Passw0rd1", UserStatus.ACTIVE);
        foreignTeacher = AuthFixtures.createStaff(users, passwords, foreignCentre, UserRole.TEACHER, "foreign@roster.test", "Foreign", "Passw0rd1", UserStatus.ACTIVE);
        Grade grade = new Grade(); grade.setCode("RG8"); grade.setName("Roster Grade"); grades.save(grade);
        Subject subject = new Subject(); subject.setSubjectName("Roster Maths"); subjects.save(subject);
        GradeSubject pairing = new GradeSubject(); pairing.setGrade(grade); pairing.setSubject(subject); pairings.save(pairing);
        batch = new Batch(); batch.setCentre(centre); batch.setGradeSubject(pairing); batch.setName("Batch A"); batches.save(batch);
        BatchMembership assignment = new BatchMembership(); assignment.setCentreId(centre.getCentreId()); assignment.setBatch(batch);
        assignment.setUser(teacher); assignment.setMemberRole(MemberRole.TEACHER); memberships.save(assignment);
    }

    @Test void createsUsernameStudentProfileMembershipAndTransactionalInvite() throws Exception {
        JsonNode created = body(mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students").with(as(teacher))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Simran Kaur\",\"contactEmail\":\"Parent@Example.test\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.get("username").asString()).isEqualTo("simran.k");
        assertThat(created.get("inviteStatus").asString()).isEqualTo("SENT");
        assertThat(created.toString().toLowerCase()).doesNotContain("pin");
        var student = users.findByCentreIdAndUserId(centre.getCentreId(), UUID.fromString(created.get("userId").asString())).orElseThrow();
        assertThat(student.getEmail()).isEqualTo("parent@example.test");
        assertThat(outbox.findByCentreId(centre.getCentreId())).singleElement().satisfies(row -> assertThat(row.getTemplate()).isEqualTo("invite"));
        assertThat(tokens.findAll()).singleElement().satisfies(token -> assertThat(token.getPurpose()).isEqualTo(AccountTokenPurpose.INVITE));
    }

    @Test void supportsCustomChecksDuplicatesRenamesAndSharedContactEmails() throws Exception {
        assertAvailable("custom.name", true); assertAvailable("Bad Name", false);
        JsonNode first = create("Ananya Rao", "family@example.test", "custom.name");
        assertThat(first.get("username").asString()).isEqualTo("custom.name");
        assertAvailable("custom.name", false);
        mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students").with(as(teacher))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Other\",\"contactEmail\":\"family@example.test\",\"username\":\"custom.name\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-USR-003"));
        JsonNode second = create("Other Student", "family@example.test", "other.student");
        mvc.perform(put("/api/v1/batches/" + batch.getBatchId() + "/students/" + second.get("userId").asString() + "/username")
                .with(as(teacher)).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"other.renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("other.renamed"));
    }

    @Test void optionalContactSkipsInviteAndExistingStudentCanJoinAnotherRoster() throws Exception {
        JsonNode created = create("No Email", null, null);
        assertThat(created.get("inviteStatus").asString()).isEqualTo("NOT_SENT");
        assertThat(outbox.findByCentreId(centre.getCentreId())).isEmpty();
        mvc.perform(get("/api/v1/centre/students").with(as(teacher))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(created.get("username").asString()));
        mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students").with(as(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"ignored\",\"userId\":\"" + created.get("userId").asString() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(created.get("userId").asString()));
    }

    @Test void resendIsCentreScopedSingleUseAndRequiresPendingContact() throws Exception {
        JsonNode created = create("Invite Student", "invite@example.test", null);
        UUID userId = UUID.fromString(created.get("userId").asString());
        mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students/" + userId + "/resend-invite").with(as(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inviteStatus").value("SENT"));
        assertThat(outbox.findByCentreId(centre.getCentreId())).hasSize(2);
        mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students/" + userId + "/resend-invite").with(as(foreignTeacher)))
                .andExpect(status().isNotFound());
        UserAccount student = users.findByCentreIdAndUserId(centre.getCentreId(), userId).orElseThrow();
        String raw = accountTokens.create(student, AccountTokenPurpose.INVITE, Duration.ofHours(1));
        accountFlows.acceptInvite(raw, "NewPass1");
        assertThatThrownBy(() -> accountFlows.acceptInvite(raw, "OtherPass1"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_TOKEN_INVALID));
    }

    @Test void rosterResponseHasUsernameInviteStateAndNoPinFields() throws Exception {
        create("Roster Student", "roster@example.test", null);
        String response = mvc.perform(get("/api/v1/batches/" + batch.getBatchId() + "/students").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].inviteStatus").value("PENDING")).andReturn().getResponse().getContentAsString();
        assertThat(response.toLowerCase()).doesNotContain("pin");
    }

    @Test void bulkAddAndDeactivateUseTheStaffRosterBoundary() throws Exception {
        mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students/bulk").with(as(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullNames\":[\"Asha Rao\",\"Bala Singh\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].username").value("asha.r"))
                .andExpect(jsonPath("$[1].username").value("bala.s"));
        UUID membershipId = memberships.findByCentreIdAndBatchIdAndMemberRole(
                centre.getCentreId(), batch.getBatchId(), MemberRole.STUDENT).get(0).getMembershipId();
        mvc.perform(delete("/api/v1/batches/" + batch.getBatchId() + "/students/" + membershipId).with(as(teacher)))
                .andExpect(status().isNoContent());
        assertThat(memberships.findById(membershipId)).get().extracting(BatchMembership::getStatus)
                .isEqualTo(MembershipStatus.INACTIVE);
    }

    private JsonNode create(String name, String email, String username) throws Exception {
        var map = new LinkedHashMap<String,Object>(); map.put("displayName", name);
        if (email != null) map.put("contactEmail", email); if (username != null) map.put("username", username);
        return body(mvc.perform(post("/api/v1/batches/" + batch.getBatchId() + "/students").with(as(teacher))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(map)))
                .andExpect(status().isCreated()).andReturn());
    }
    private void assertAvailable(String value, boolean available) throws Exception {
        mvc.perform(get("/api/v1/centre/username-check").param("value", value).with(as(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(available));
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(UserAccount user) {
        var principal = LearniumPrincipal.staff(user.getUserId(), user.getCentre().getCentreId(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
