package com.learnium.report;

import com.learnium.api.dto.ReportDtos.BatchTopicDto;
import com.learnium.api.dto.ReportDtos.BatchTopicReportDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch weak-topic report (M6 task 29). Staff only: a teacher sees batches
 * they manage, the centre admin sees the whole centre; anything else reads
 * as not found, so batch existence never leaks across scopes.
 */
@Service
public class BatchReportService {

    private final BatchRepository batches;
    private final BatchMembershipRepository memberships;
    private final ReportFactsService facts;

    public BatchReportService(
            BatchRepository batches, BatchMembershipRepository memberships, ReportFactsService facts) {
        this.batches = batches;
        this.memberships = memberships;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public BatchTopicReportDto topicReport(LearniumPrincipal caller, UUID batchId) {
        Batch batch = batches.findByCentreIdAndBatchId(caller.centreId(), batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TST_NOT_FOUND));
        if (caller.role() == UserRole.TEACHER
                && !memberships.existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                        caller.centreId(),
                        batchId,
                        caller.userId(),
                        MemberRole.TEACHER,
                        MembershipStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.TST_NOT_FOUND);
        }
        var topics = facts.batchTopicFacts(caller.centreId(), batchId).stream()
                .map(fact -> new BatchTopicDto(
                        fact.topicId(),
                        fact.topicName(),
                        fact.confirmedItems(),
                        fact.pendingItems(),
                        fact.studentCount(),
                        fact.earnedMarks(),
                        fact.availableMarks(),
                        fact.percentage(),
                        fact.band()))
                .toList();
        return new BatchTopicReportDto(batch.getBatchId(), batch.getName(), topics);
    }
}
