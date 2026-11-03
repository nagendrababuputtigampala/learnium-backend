package com.learnium.admin;

import com.learnium.api.dto.BatchDtos.*;
import com.learnium.auth.AccountFlowService;
import com.learnium.common.EmailAddresses;
import com.learnium.common.error.*;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.*;
import com.learnium.repository.user.*;
import com.learnium.security.LearniumPrincipal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Centre-scoped batch roster management for real student accounts. */
@Service
public class RosterService {
    private final BatchAccessGuard access;
    private final BatchMembershipRepository memberships;
    private final UserAccountRepository users;
    private final StudentProfileRepository profiles;
    private final StudentUsernameService usernames;
    private final AccountFlowService accountFlows;

    public RosterService(BatchAccessGuard access, BatchMembershipRepository memberships,
            UserAccountRepository users, StudentProfileRepository profiles,
            StudentUsernameService usernames, AccountFlowService accountFlows) {
        this.access = access; this.memberships = memberships; this.users = users; this.profiles = profiles;
        this.usernames = usernames; this.accountFlows = accountFlows;
    }

    @Transactional(readOnly = true)
    public List<RosterStudentDto> listStudents(LearniumPrincipal caller, UUID batchId) {
        access.requireBatchAccess(caller, batchId);
        return memberships.findByCentreIdAndBatchIdAndMemberRole(caller.centreId(), batchId, MemberRole.STUDENT)
                .stream().map(this::dto).toList();
    }

    @Transactional
    public AddStudentResponse addStudent(LearniumPrincipal caller, UUID batchId, AddStudentRequest request) {
        Batch batch = access.requireBatchAccess(caller, batchId);
        if (request.userId() != null) return addExisting(caller, batch, request.userId());
        return createStudent(batch, request.displayName().trim(), request.contactEmail(), request.username());
    }

    @Transactional
    public List<AddStudentResponse> bulkAddStudents(LearniumPrincipal caller, UUID batchId, List<String> names) {
        Batch batch = access.requireBatchAccess(caller, batchId);
        return names.stream().map(name -> createStudent(batch, name.trim(), null, null)).toList();
    }

    @Transactional
    public RosterStudentDto rename(LearniumPrincipal caller, UUID batchId, UUID userId, String requested) {
        access.requireBatchAccess(caller, batchId);
        UserAccount student = requireStudent(caller, batchId, userId);
        String username = usernames.requireAvailable(caller.centreId(), requested, student.getUserId());
        if (!username.equals(student.getUsername())) {
            student.setUsername(username); users.saveAndFlush(student);
        }
        return dto(memberships.findByCentreIdAndBatchIdAndUserId(caller.centreId(), batchId, userId).orElseThrow());
    }

    @Transactional
    public AddStudentResponse resendInvite(LearniumPrincipal caller, UUID batchId, UUID userId) {
        access.requireBatchAccess(caller, batchId);
        UserAccount student = requireStudent(caller, batchId, userId);
        if (student.getPasswordHash() != null)
            throw new BusinessException(ErrorCode.VAL_FAILED, "This student has already accepted their invite");
        if (student.getEmail() == null || student.getEmail().isBlank())
            throw new BusinessException(ErrorCode.VAL_FAILED, "Add a contact email before resending the invite");
        accountFlows.resendInvite(student);
        var membership = memberships.findByCentreIdAndBatchIdAndUserId(caller.centreId(), batchId, userId).orElseThrow();
        return response(membership, "SENT");
    }

    @Transactional
    public void deactivateStudent(LearniumPrincipal caller, UUID batchId, UUID membershipId) {
        access.requireBatchAccess(caller, batchId);
        BatchMembership membership = memberships.findByCentreIdAndMembershipId(caller.centreId(), membershipId)
                .filter(row -> row.getBatch().getBatchId().equals(batchId) && row.getMemberRole() == MemberRole.STUDENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.CTR_ROSTER_NOT_FOUND));
        membership.setStatus(MembershipStatus.INACTIVE); memberships.save(membership);
    }

    private AddStudentResponse addExisting(LearniumPrincipal caller, Batch batch, UUID userId) {
        UserAccount student = users.findByCentreIdAndUserId(caller.centreId(), userId)
                .filter(user -> user.getRole() == UserRole.STUDENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.USR_NOT_FOUND));
        BatchMembership membership = memberships.findByCentreIdAndBatchIdAndUserId(caller.centreId(), batch.getBatchId(), userId)
                .orElseGet(() -> membership(batch, student));
        membership.setStatus(MembershipStatus.ACTIVE); memberships.saveAndFlush(membership);
        return response(membership, inviteStatus(student));
    }

    private AddStudentResponse createStudent(Batch batch, String displayName, String contactEmail, String requestedUsername) {
        UUID centreId = batch.getCentre().getCentreId();
        UserAccount student = new UserAccount();
        student.setCentre(batch.getCentre()); student.setRole(UserRole.STUDENT); student.setDisplayName(displayName);
        student.setEmail(contactEmail == null || contactEmail.isBlank() ? null : EmailAddresses.normalize(contactEmail));
        student.setUsername(usernames.choose(centreId, displayName, requestedUsername));
        student.setPasswordHash(null); student.setStatus(UserStatus.ACTIVE); student = users.saveAndFlush(student);
        StudentProfile profile = new StudentProfile(); profile.setUser(student); profile.setCentreId(centreId);
        profile.setFullName(displayName); profile.setGrade(batch.getGradeSubject().getGrade()); profiles.save(profile);
        BatchMembership membership = membership(batch, student); memberships.saveAndFlush(membership);
        boolean sent = accountFlows.sendStudentInvite(student);
        return response(membership, sent ? "SENT" : "NOT_SENT");
    }

    private BatchMembership membership(Batch batch, UserAccount student) {
        BatchMembership membership = new BatchMembership(); membership.setCentreId(batch.getCentre().getCentreId());
        membership.setBatch(batch); membership.setUser(student); membership.setMemberRole(MemberRole.STUDENT);
        membership.setStatus(MembershipStatus.ACTIVE); return membership;
    }

    private UserAccount requireStudent(LearniumPrincipal caller, UUID batchId, UUID userId) {
        return memberships.findByCentreIdAndBatchIdAndUserId(caller.centreId(), batchId, userId)
                .filter(row -> row.getMemberRole() == MemberRole.STUDENT).map(BatchMembership::getUser)
                .orElseThrow(() -> new BusinessException(ErrorCode.CTR_ROSTER_NOT_FOUND));
    }

    private RosterStudentDto dto(BatchMembership row) {
        UserAccount user = row.getUser();
        return new RosterStudentDto(row.getMembershipId(), user.getUserId(), user.getDisplayName(), user.getUsername(),
                user.getEmail(), row.getStatus().name(), inviteStatus(user));
    }
    private AddStudentResponse response(BatchMembership row, String status) {
        UserAccount user = row.getUser();
        return new AddStudentResponse(row.getMembershipId(), user.getUserId(), user.getDisplayName(), user.getUsername(), status);
    }
    private String inviteStatus(UserAccount user) {
        if (user.getPasswordHash() != null) return "ACCEPTED";
        return user.getEmail() == null ? "NOT_SENT" : "PENDING";
    }
}
