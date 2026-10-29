package com.learnium.admin;

import com.learnium.api.dto.BatchDtos.BatchDto;
import com.learnium.api.dto.BatchDtos.CreateBatchRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch management. Teacher assignment is a TEACHER membership row on the
 * batch roster — the same row the access guard and the roster read — so
 * there is exactly one source of truth for "who teaches this batch".
 */
@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final BatchMembershipRepository batchMembershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final CentreRepository centreRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final BatchAccessGuard batchAccessGuard;

    public BatchService(
            BatchRepository batchRepository,
            BatchMembershipRepository batchMembershipRepository,
            UserAccountRepository userAccountRepository,
            CentreRepository centreRepository,
            GradeSubjectRepository gradeSubjectRepository,
            BatchAccessGuard batchAccessGuard) {
        this.batchRepository = batchRepository;
        this.batchMembershipRepository = batchMembershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.centreRepository = centreRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.batchAccessGuard = batchAccessGuard;
    }

    @Transactional
    public BatchDto createBatch(LearniumPrincipal principal, CreateBatchRequest request) {
        GradeSubject gradeSubject =
                gradeSubjectRepository
                        .findById(request.gradeSubjectId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.VAL_FAILED, "Unknown gradeSubjectId"));
        Centre centre =
                centreRepository
                        .findById(principal.centreId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName(request.name());
        batch.setActive(true);
        return toDto(batchRepository.save(batch), principal.centreId());
    }

    /** Admins see every centre batch; teachers only the ones they teach. */
    @Transactional(readOnly = true)
    public List<BatchDto> listBatches(LearniumPrincipal principal) {
        List<Batch> batches =
                principal.role() == UserRole.CENTRE_ADMIN
                        ? batchRepository.findByCentreId(principal.centreId())
                        : batchMembershipRepository.findBatchesByCentreIdAndUserIdAndMemberRoleAndStatus(
                                principal.centreId(),
                                principal.userId(),
                                MemberRole.TEACHER,
                                MembershipStatus.ACTIVE);
        return batches.stream().map(batch -> toDto(batch, principal.centreId())).toList();
    }

    @Transactional
    public BatchDto assignTeacher(LearniumPrincipal principal, UUID batchId, UUID userId) {
        Batch batch = batchAccessGuard.requireBatchAccess(principal, batchId);
        UserAccount teacher =
                userAccountRepository
                        .findByCentreIdAndUserId(principal.centreId(), userId)
                        .filter(
                                user ->
                                        user.getRole() == UserRole.TEACHER
                                                && user.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USR_NOT_FOUND));
        BatchMembership membership =
                batchMembershipRepository
                        .findByCentreIdAndBatchIdAndUserId(principal.centreId(), batchId, userId)
                        .orElse(null);
        if (membership == null) {
            membership = new BatchMembership();
            membership.setCentreId(principal.centreId());
            membership.setBatch(batch);
            membership.setUser(teacher);
            membership.setMemberRole(MemberRole.TEACHER);
        } else if (membership.getMemberRole() != MemberRole.TEACHER) {
            throw new BusinessException(
                    ErrorCode.VAL_FAILED, "User is on this batch's roster as a student");
        }
        membership.setStatus(MembershipStatus.ACTIVE);
        batchMembershipRepository.save(membership);
        return toDto(batch, principal.centreId());
    }

    @Transactional
    public void unassignTeacher(LearniumPrincipal principal, UUID batchId, UUID userId) {
        batchAccessGuard.requireBatchAccess(principal, batchId);
        BatchMembership membership =
                batchMembershipRepository
                        .findByCentreIdAndBatchIdAndUserId(principal.centreId(), batchId, userId)
                        .filter(
                                m ->
                                        m.getMemberRole() == MemberRole.TEACHER
                                                && m.getStatus() == MembershipStatus.ACTIVE)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CTR_ROSTER_NOT_FOUND));
        membership.setStatus(MembershipStatus.INACTIVE);
        batchMembershipRepository.save(membership);
    }

    private BatchDto toDto(Batch batch, UUID centreId) {
        List<BatchMembership> teachers =
                batchMembershipRepository.findByCentreIdAndBatchIdAndMemberRoleAndStatus(
                        centreId, batch.getBatchId(), MemberRole.TEACHER, MembershipStatus.ACTIVE);
        BatchMembership teacher = teachers.isEmpty() ? null : teachers.get(0);
        long studentCount =
                batchMembershipRepository.countByCentreIdAndBatchIdAndMemberRoleAndStatus(
                        centreId, batch.getBatchId(), MemberRole.STUDENT, MembershipStatus.ACTIVE);
        GradeSubject gradeSubject = batch.getGradeSubject();
        return new BatchDto(
                batch.getBatchId(),
                batch.getName(),
                gradeSubject.getGradeSubjectId(),
                gradeSubject.getGrade().getName() + " · " + gradeSubject.getSubject().getSubjectName(),
                teacher == null ? null : teacher.getUser().getUserId(),
                teacher == null ? null : teacher.getUser().getDisplayName(),
                studentCount);
    }
}
