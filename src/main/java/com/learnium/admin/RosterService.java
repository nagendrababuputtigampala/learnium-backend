package com.learnium.admin;

import com.learnium.api.dto.BatchDtos.AddStudentResponse;
import com.learnium.api.dto.BatchDtos.ResetPinResponse;
import com.learnium.api.dto.BatchDtos.RosterStudentDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.StudentProfile;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.StudentProfileRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch rosters. Every operation passes through {@link BatchAccessGuard}
 * first (centre tenancy + teacher assignment), then works centre-scoped.
 * Adding a student creates the account, profile and roster membership in one
 * transaction and returns the generated 4-digit PIN exactly once — only its
 * BCrypt hash is stored.
 */
@Service
public class RosterService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BatchAccessGuard batchAccessGuard;
    private final BatchMembershipRepository batchMembershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public RosterService(
            BatchAccessGuard batchAccessGuard,
            BatchMembershipRepository batchMembershipRepository,
            UserAccountRepository userAccountRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.batchAccessGuard = batchAccessGuard;
        this.batchMembershipRepository = batchMembershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<RosterStudentDto> listStudents(LearniumPrincipal principal, UUID batchId) {
        batchAccessGuard.requireBatchAccess(principal, batchId);
        return batchMembershipRepository
                .findByCentreIdAndBatchIdAndMemberRole(
                        principal.centreId(), batchId, MemberRole.STUDENT)
                .stream()
                .map(
                        membership ->
                                new RosterStudentDto(
                                        membership.getMembershipId(),
                                        membership.getUser().getUserId(),
                                        membership.getUser().getDisplayName(),
                                        membership.getStatus().name(),
                                        membership.isPinLocked()))
                .toList();
    }

    @Transactional
    public AddStudentResponse addStudent(LearniumPrincipal principal, UUID batchId, String fullName) {
        Batch batch = batchAccessGuard.requireBatchAccess(principal, batchId);
        return createStudent(batch, fullName.trim());
    }

    /** Single transaction: either the whole class list lands or none of it does. */
    @Transactional
    public List<AddStudentResponse> bulkAddStudents(
            LearniumPrincipal principal, UUID batchId, List<String> fullNames) {
        Batch batch = batchAccessGuard.requireBatchAccess(principal, batchId);
        return fullNames.stream().map(name -> createStudent(batch, name.trim())).toList();
    }

    @Transactional
    public ResetPinResponse resetPin(LearniumPrincipal principal, UUID batchId, UUID membershipId) {
        batchAccessGuard.requireBatchAccess(principal, batchId);
        BatchMembership membership = requireStudentMembership(principal, batchId, membershipId);
        String pin = newPin();
        membership.setPinHash(passwordEncoder.encode(pin));
        membership.setPinFailedAttempts(0);
        membership.setPinLocked(false);
        batchMembershipRepository.save(membership);
        return new ResetPinResponse(pin);
    }

    @Transactional
    public void deactivateStudent(LearniumPrincipal principal, UUID batchId, UUID membershipId) {
        batchAccessGuard.requireBatchAccess(principal, batchId);
        BatchMembership membership = requireStudentMembership(principal, batchId, membershipId);
        membership.setStatus(MembershipStatus.INACTIVE);
        batchMembershipRepository.save(membership);
    }

    private BatchMembership requireStudentMembership(
            LearniumPrincipal principal, UUID batchId, UUID membershipId) {
        return batchMembershipRepository
                .findByCentreIdAndMembershipId(principal.centreId(), membershipId)
                .filter(
                        membership ->
                                membership.getBatch().getBatchId().equals(batchId)
                                        && membership.getMemberRole() == MemberRole.STUDENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.CTR_ROSTER_NOT_FOUND));
    }

    private AddStudentResponse createStudent(Batch batch, String fullName) {
        UserAccount user = new UserAccount();
        user.setCentre(batch.getCentre());
        user.setRole(UserRole.STUDENT);
        user.setEmail(null);
        user.setDisplayName(fullName);
        user.setPasswordHash(null);
        user.setStatus(UserStatus.ACTIVE);
        user = userAccountRepository.save(user);

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setCentreId(batch.getCentre().getCentreId());
        profile.setFullName(fullName);
        profile.setGrade(batch.getGradeSubject().getGrade());
        studentProfileRepository.save(profile);

        String pin = newPin();
        BatchMembership membership = new BatchMembership();
        membership.setCentreId(batch.getCentre().getCentreId());
        membership.setBatch(batch);
        membership.setUser(user);
        membership.setMemberRole(MemberRole.STUDENT);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setPinHash(passwordEncoder.encode(pin));
        membership = batchMembershipRepository.save(membership);

        return new AddStudentResponse(membership.getMembershipId(), user.getUserId(), fullName, pin);
    }

    private static String newPin() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }
}
