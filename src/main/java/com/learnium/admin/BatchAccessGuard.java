package com.learnium.admin;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Batch;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place batch-level authorization is decided: a batch must belong to
 * the caller's centre (else it "does not exist", LRN-CTR-001 — cross-centre
 * ids are never confirmed), and a TEACHER must additionally hold an ACTIVE
 * teacher membership in it (else LRN-AUTH-003). Centre admins reach every
 * batch of their own centre.
 */
@Component
public class BatchAccessGuard {

    private final BatchRepository batchRepository;
    private final BatchMembershipRepository batchMembershipRepository;

    public BatchAccessGuard(
            BatchRepository batchRepository, BatchMembershipRepository batchMembershipRepository) {
        this.batchRepository = batchRepository;
        this.batchMembershipRepository = batchMembershipRepository;
    }

    @Transactional(readOnly = true)
    public Batch requireBatchAccess(LearniumPrincipal principal, UUID batchId) {
        Batch batch =
                batchRepository
                        .findByCentreIdAndBatchId(principal.centreId(), batchId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CTR_BATCH_NOT_FOUND));
        if (principal.role() == UserRole.TEACHER) {
            boolean assigned =
                    batchMembershipRepository
                            .existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                                    principal.centreId(),
                                    batchId,
                                    principal.userId(),
                                    MemberRole.TEACHER,
                                    MembershipStatus.ACTIVE);
            if (!assigned) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
        }
        return batch;
    }
}
