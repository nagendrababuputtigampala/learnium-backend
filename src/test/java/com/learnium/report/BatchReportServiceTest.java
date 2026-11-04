package com.learnium.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Batch;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Scope rules of the batch topic report: teachers only manage, admins see the centre. */
class BatchReportServiceTest {
    private final BatchRepository batches = mock(BatchRepository.class);
    private final BatchMembershipRepository memberships = mock(BatchMembershipRepository.class);
    private final ReportFactsService facts = mock(ReportFactsService.class);
    private final BatchReportService service = new BatchReportService(batches, memberships, facts);
    private final UUID centreId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();

    private Batch batch() {
        Centre centre = new Centre();
        centre.setCentreId(centreId);
        Batch batch = new Batch();
        batch.setBatchId(batchId);
        batch.setCentre(centre);
        batch.setName("Batch 8");
        return batch;
    }

    @Test
    void adminReadsWithoutMembership() {
        when(batches.findByCentreIdAndBatchId(centreId, batchId)).thenReturn(Optional.of(batch()));
        when(facts.batchTopicFacts(centreId, batchId)).thenReturn(List.of());

        var report = service.topicReport(
                LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN), batchId);

        assertThat(report.batchName()).isEqualTo("Batch 8");
        assertThat(report.topics()).isEmpty();
    }

    @Test
    void managingTeacherReadsAndUnmanagedTeacherGetsNotFound() {
        UUID teacherId = UUID.randomUUID();
        when(batches.findByCentreIdAndBatchId(centreId, batchId)).thenReturn(Optional.of(batch()));
        when(memberships.existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                        centreId, batchId, teacherId, MemberRole.TEACHER, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(facts.batchTopicFacts(centreId, batchId)).thenReturn(List.of());
        LearniumPrincipal teacher = LearniumPrincipal.staff(teacherId, centreId, UserRole.TEACHER);

        assertThat(service.topicReport(teacher, batchId).batchId()).isEqualTo(batchId);

        UUID otherTeacherId = UUID.randomUUID();
        when(memberships.existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
                        centreId, batchId, otherTeacherId, MemberRole.TEACHER, MembershipStatus.ACTIVE))
                .thenReturn(false);
        assertThatThrownBy(() -> service.topicReport(
                        LearniumPrincipal.staff(otherTeacherId, centreId, UserRole.TEACHER), batchId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TST_NOT_FOUND));
    }

    @Test
    void missingBatchIsNotFound() {
        when(batches.findByCentreIdAndBatchId(centreId, batchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.topicReport(
                        LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN), batchId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TST_NOT_FOUND));
    }
}
