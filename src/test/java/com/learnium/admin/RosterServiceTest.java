package com.learnium.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.learnium.api.dto.BatchDtos.AddStudentRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.common.error.BusinessException;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RosterServiceTest {
    private final BatchAccessGuard access = mock(BatchAccessGuard.class);
    private final BatchMembershipRepository memberships = mock(BatchMembershipRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final StudentProfileRepository profiles = mock(StudentProfileRepository.class);
    private final StudentUsernameService usernames = mock(StudentUsernameService.class);
    private final AccountFlowService accountFlows = mock(AccountFlowService.class);
    private final RosterService service = new RosterService(access, memberships, users, profiles, usernames, accountFlows);
    private final UUID centreId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LearniumPrincipal caller = LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.TEACHER);
    private Centre centre;
    private Batch batch;
    private UserAccount student;
    private BatchMembership membership;

    @BeforeEach
    void setup() {
        centre = new Centre(); centre.setCentreId(centreId); centre.setCode("tc");
        Grade grade = new Grade(); grade.setCode("G8");
        GradeSubject pairing = new GradeSubject(); pairing.setGrade(grade);
        batch = new Batch(); batch.setBatchId(batchId); batch.setCentre(centre); batch.setGradeSubject(pairing);
        student = new UserAccount(); student.setUserId(userId); student.setCentre(centre); student.setRole(UserRole.STUDENT);
        student.setDisplayName("Student One"); student.setUsername("student.o");
        membership = member(batch, student, MemberRole.STUDENT);
        membership.setMembershipId(UUID.randomUUID());
        when(access.requireBatchAccess(caller, batchId)).thenReturn(batch);
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberships.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void bulkAddCreatesAccountsAndTreatsBlankContactAsAbsent() {
        when(usernames.choose(eq(centreId), anyString(), isNull())).thenReturn("amy", "bob");
        when(accountFlows.sendStudentInvite(any())).thenReturn(false);

        assertThat(service.bulkAddStudents(caller, batchId, List.of(" Amy ", "Bob")))
                .extracting(response -> response.displayName(), response -> response.inviteStatus())
                .containsExactly(tuple("Amy", "NOT_SENT"), tuple("Bob", "NOT_SENT"));
        assertThat(service.addStudent(caller, batchId, new AddStudentRequest(" Cara ", " ", null, null)).inviteStatus())
                .isEqualTo("NOT_SENT");
        verify(profiles, times(3)).save(any());
    }

    @Test
    void renameNoopsForCurrentNameAndRejectsNonStudentMembership() {
        when(memberships.findByCentreIdAndBatchIdAndUserId(centreId, batchId, userId))
                .thenReturn(Optional.of(membership));
        when(usernames.requireAvailable(centreId, "student.o", userId)).thenReturn("student.o");
        assertThat(service.rename(caller, batchId, userId, "student.o").username()).isEqualTo("student.o");
        verify(users, never()).saveAndFlush(student);

        membership.setMemberRole(MemberRole.TEACHER);
        assertThatThrownBy(() -> service.rename(caller, batchId, userId, "new.name"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resendRejectsAcceptedMissingAndBlankContacts() {
        when(memberships.findByCentreIdAndBatchIdAndUserId(centreId, batchId, userId))
                .thenReturn(Optional.of(membership));
        student.setPasswordHash("hash");
        assertThatThrownBy(() -> service.resendInvite(caller, batchId, userId)).isInstanceOf(BusinessException.class);
        student.setPasswordHash(null); student.setEmail(null);
        assertThatThrownBy(() -> service.resendInvite(caller, batchId, userId)).isInstanceOf(BusinessException.class);
        student.setEmail(" ");
        assertThatThrownBy(() -> service.resendInvite(caller, batchId, userId)).isInstanceOf(BusinessException.class);
    }

    @Test
    void deactivationRequiresTheExactStudentMembership() {
        when(memberships.findByCentreIdAndMembershipId(centreId, membership.getMembershipId()))
                .thenReturn(Optional.of(membership));
        service.deactivateStudent(caller, batchId, membership.getMembershipId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);

        Batch other = new Batch(); other.setBatchId(UUID.randomUUID()); membership.setBatch(other);
        assertThatThrownBy(() -> service.deactivateStudent(caller, batchId, membership.getMembershipId()))
                .isInstanceOf(BusinessException.class);
        membership.setBatch(batch); membership.setMemberRole(MemberRole.TEACHER);
        assertThatThrownBy(() -> service.deactivateStudent(caller, batchId, membership.getMembershipId()))
                .isInstanceOf(BusinessException.class);
        when(memberships.findByCentreIdAndMembershipId(centreId, membership.getMembershipId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivateStudent(caller, batchId, membership.getMembershipId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addExistingRejectsMissingAndStaffAccountsThenCreatesOrReactivatesMembership() {
        AddStudentRequest request = new AddStudentRequest("Ignored", null, null, userId);
        when(users.findByCentreIdAndUserId(centreId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addStudent(caller, batchId, request)).isInstanceOf(BusinessException.class);
        student.setRole(UserRole.TEACHER);
        when(users.findByCentreIdAndUserId(centreId, userId)).thenReturn(Optional.of(student));
        assertThatThrownBy(() -> service.addStudent(caller, batchId, request)).isInstanceOf(BusinessException.class);

        student.setRole(UserRole.STUDENT);
        when(memberships.findByCentreIdAndBatchIdAndUserId(centreId, batchId, userId)).thenReturn(Optional.empty());
        assertThat(service.addStudent(caller, batchId, request).username()).isEqualTo("student.o");
        when(memberships.findByCentreIdAndBatchIdAndUserId(centreId, batchId, userId)).thenReturn(Optional.of(membership));
        assertThat(service.addStudent(caller, batchId, request).username()).isEqualTo("student.o");
    }

    @Test
    void listReportsAcceptedInviteState() {
        student.setPasswordHash("hash");
        when(memberships.findByCentreIdAndBatchIdAndMemberRole(centreId, batchId, MemberRole.STUDENT))
                .thenReturn(List.of(membership));
        assertThat(service.listStudents(caller, batchId)).singleElement()
                .satisfies(row -> assertThat(row.inviteStatus()).isEqualTo("ACCEPTED"));
    }

    private BatchMembership member(Batch owner, UserAccount user, MemberRole role) {
        BatchMembership row = new BatchMembership(); row.setCentreId(centreId); row.setBatch(owner);
        row.setUser(user); row.setMemberRole(role); row.setStatus(MembershipStatus.ACTIVE); return row;
    }
}
