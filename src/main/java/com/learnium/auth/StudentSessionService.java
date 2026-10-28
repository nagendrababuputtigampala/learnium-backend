package com.learnium.auth;

import com.learnium.api.dto.AuthDtos.StudentSessionResponse;
import com.learnium.api.dto.AuthDtos.StudentSummary;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.security.JwtService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The student door: batch id + roster name + 4-digit PIN. The batch id is
 * the locator (students pick it from the public list), so the batch lookup
 * is deliberately global — everything after it is scoped to that batch's
 * centre and roster. Failure responses never reveal which part failed:
 * unknown name and wrong PIN are the same 401; only a fully locked match
 * set earns the distinct 403. Five wrong PINs lock a membership (an admin
 * or teacher reset unlocks it).
 *
 * <p>Failed attempts are recorded in an independent REQUIRES_NEW transaction
 * per membership: the rejection thrown afterwards must never roll the
 * counter back. (The previous implementation incremented inside the request
 * transaction and then threw — the rollback reset the counter, so the
 * lockout could never actually engage across separate requests.)
 */
@Service
public class StudentSessionService {

    static final int MAX_PIN_FAILURES = 5;

    private final BatchRepository batchRepository;
    private final BatchMembershipRepository batchMembershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate requiresNewTemplate;

    public StudentSessionService(
            BatchRepository batchRepository,
            BatchMembershipRepository batchMembershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PlatformTransactionManager transactionManager) {
        this.batchRepository = batchRepository;
        this.batchMembershipRepository = batchMembershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = requiresNew;
    }

    @Transactional
    public StudentSessionResponse openSession(UUID batchId, String fullName, String pin) {
        Batch batch =
                batchRepository
                        .findById(batchId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CTR_BATCH_NOT_FOUND));
        String wanted = fullName.trim();
        List<BatchMembership> matches =
                batchMembershipRepository
                        .findByCentreIdAndBatchIdAndMemberRoleAndStatus(
                                batch.getCentre().getCentreId(),
                                batchId,
                                MemberRole.STUDENT,
                                MembershipStatus.ACTIVE)
                        .stream()
                        .filter(
                                membership ->
                                        membership
                                                .getUser()
                                                .getDisplayName()
                                                .trim()
                                                .equalsIgnoreCase(wanted))
                        .toList();
        if (matches.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_STUDENT_REJECTED);
        }
        if (matches.stream().allMatch(BatchMembership::isPinLocked)) {
            throw new BusinessException(ErrorCode.AUTH_PIN_LOCKED);
        }
        for (BatchMembership membership : matches) {
            if (!membership.isPinLocked()
                    && membership.getPinHash() != null
                    && passwordEncoder.matches(pin, membership.getPinHash())) {
                membership.setPinFailedAttempts(0);
                membership.setPinLocked(false);
                batchMembershipRepository.save(membership);
                String token =
                        jwtService.issueStudentToken(
                                membership.getUser(),
                                batch.getCentre().getCentreId(),
                                batchId,
                                membership.getMembershipId());
                return new StudentSessionResponse(
                        token,
                        "Bearer",
                        JwtService.STUDENT_TOKEN_TTL.toSeconds(),
                        new StudentSummary(
                                membership.getUser().getUserId(),
                                membership.getUser().getDisplayName(),
                                membership.getMembershipId(),
                                batchId,
                                batch.getName(),
                                batch.getCentre().getCentreId()));
            }
        }
        // No match accepted the PIN: record the failure on every matched
        // membership, each in its own committed transaction, THEN reject.
        for (BatchMembership membership : matches) {
            requiresNewTemplate.executeWithoutResult(
                    status ->
                            batchMembershipRepository.incrementPinFailures(
                                    membership.getMembershipId(), MAX_PIN_FAILURES));
        }
        throw new BusinessException(ErrorCode.AUTH_STUDENT_REJECTED);
    }
}
