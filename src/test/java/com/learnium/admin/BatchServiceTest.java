package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.BatchDtos.CreateBatchRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.UserRole;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Batch creation when the caller's centre row itself is missing. */
class BatchServiceTest {

    @Test
    void createBatchWithMissingCentreThrowsSysNotFound() {
        GradeSubjectRepository gradeSubjectRepository = mock(GradeSubjectRepository.class);
        CentreRepository centreRepository = mock(CentreRepository.class);
        UUID gradeSubjectId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();
        when(gradeSubjectRepository.findById(gradeSubjectId))
                .thenReturn(Optional.of(new GradeSubject()));
        when(centreRepository.findById(centreId)).thenReturn(Optional.empty());
        BatchService service =
                new BatchService(
                        mock(BatchRepository.class),
                        mock(BatchMembershipRepository.class),
                        mock(UserAccountRepository.class),
                        centreRepository,
                        gradeSubjectRepository,
                        mock(BatchAccessGuard.class));
        LearniumPrincipal principal =
                LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);

        assertThatThrownBy(
                        () -> service.createBatch(principal, new CreateBatchRequest("Batch B", gradeSubjectId)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }
}
