package com.learnium.model.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.config.JpaAuditingConfig;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.StudentProfileRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class TenancyPersistenceTest {

    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private BatchMembershipRepository membershipRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private TestEntityManager entityManager;

    private Centre saveCentre(String name) {
        Centre centre = new Centre();
        centre.setName(name);
        centre.setContactEmail("office@example.com");
        return centreRepository.save(centre);
    }

    private UserAccount saveUser(Centre centre, UserRole role, String email, String displayName) {
        UserAccount user = new UserAccount();
        user.setCentre(centre);
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return userAccountRepository.save(user);
    }

    private GradeSubject saveGradeSubject() {
        Grade grade = new Grade();
        grade.setCode("G8");
        grade.setName("Grade 8");
        grade = gradeRepository.save(grade);

        Subject subject = new Subject();
        subject.setSubjectName("Mathematics");
        subject = subjectRepository.save(subject);

        GradeSubject gradeSubject = new GradeSubject();
        gradeSubject.setGrade(grade);
        gradeSubject.setSubject(subject);
        return gradeSubjectRepository.save(gradeSubject);
    }

    @Test
    void savesAndReloadsRosterMembership() {
        Centre centre = saveCentre("Alpha Tutoring");
        GradeSubject gradeSubject = saveGradeSubject();
        UserAccount teacher = saveUser(centre, UserRole.TEACHER, "patel@example.com", "Mrs. Patel");
        UserAccount student = saveUser(centre, UserRole.STUDENT, null, "Simran");

        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName("Grade 8 Math — Batch B");
        batch = batchRepository.save(batch);

        BatchMembership teacherMembership = new BatchMembership();
        teacherMembership.setBatch(batch);
        teacherMembership.setCentreId(centre.getCentreId());
        teacherMembership.setUser(teacher);
        teacherMembership.setMemberRole(MemberRole.TEACHER);
        membershipRepository.save(teacherMembership);

        BatchMembership studentMembership = new BatchMembership();
        studentMembership.setBatch(batch);
        studentMembership.setCentreId(centre.getCentreId());
        studentMembership.setUser(student);
        studentMembership.setMemberRole(MemberRole.STUDENT);
        studentMembership.setPinHash("hashed-pin");
        membershipRepository.save(studentMembership);

        entityManager.flush();
        entityManager.clear();

        List<BatchMembership> roster =
                membershipRepository.findByCentreIdAndBatchIdAndStatus(
                        centre.getCentreId(), batch.getBatchId(), MembershipStatus.ACTIVE);
        assertThat(roster).hasSize(2);
        assertThat(roster)
                .extracting(m -> m.getUser().getUserId())
                .containsExactlyInAnyOrder(teacher.getUserId(), student.getUserId());
        assertThat(
                        roster.stream()
                                .filter(m -> m.getMemberRole() == MemberRole.STUDENT)
                                .findFirst()
                                .orElseThrow()
                                .getPinHash())
                .isEqualTo("hashed-pin");
        assertThat(
                        membershipRepository.findByCentreIdAndBatchIdAndStatus(
                                centre.getCentreId(), batch.getBatchId(), MembershipStatus.INACTIVE))
                .isEmpty();
        assertThat(
                        membershipRepository.existsByCentreIdAndBatchIdAndUserId(
                                centre.getCentreId(), batch.getBatchId(), student.getUserId()))
                .isTrue();
        assertThat(
                        membershipRepository.existsByCentreIdAndBatchIdAndUserId(
                                centre.getCentreId(), batch.getBatchId(), teacher.getUserId()))
                .isTrue();

        // A student of another centre, rostered in their OWN centre's batch, is
        // still invisible here: the centre predicate, not the absence of a row,
        // is what isolates the roster.
        Centre beta = saveCentre("Beta Tutoring");
        UserAccount outsider = saveUser(beta, UserRole.STUDENT, null, "Outsider");
        Batch betaBatch = new Batch();
        betaBatch.setCentre(beta);
        betaBatch.setGradeSubject(gradeSubject);
        betaBatch.setName("Beta Batch");
        betaBatch = batchRepository.save(betaBatch);
        BatchMembership outsiderMembership = new BatchMembership();
        outsiderMembership.setBatch(betaBatch);
        outsiderMembership.setCentreId(beta.getCentreId());
        outsiderMembership.setUser(outsider);
        outsiderMembership.setMemberRole(MemberRole.STUDENT);
        membershipRepository.save(outsiderMembership);
        entityManager.flush();
        entityManager.clear();

        assertThat(
                        membershipRepository.existsByCentreIdAndBatchIdAndUserId(
                                centre.getCentreId(), batch.getBatchId(), outsider.getUserId()))
                .isFalse();
        assertThat(
                        membershipRepository.findByCentreIdAndBatchIdAndStatus(
                                beta.getCentreId(), batch.getBatchId(), MembershipStatus.ACTIVE))
                .isEmpty();
        assertThat(
                        membershipRepository.findByCentreIdAndBatchIdAndStatus(
                                beta.getCentreId(), betaBatch.getBatchId(), MembershipStatus.ACTIVE))
                .extracting(m -> m.getUser().getUserId())
                .containsExactly(outsider.getUserId());
    }

    @Test
    void centreScopedUserQueryNeverReturnsAnotherCentresUsers() {
        Centre alpha = saveCentre("Alpha Tutoring");
        Centre beta = saveCentre("Beta Tutoring");
        UserAccount alphaTeacher = saveUser(alpha, UserRole.TEACHER, "a@example.com", "Alpha Teacher");
        saveUser(beta, UserRole.TEACHER, "b@example.com", "Beta Teacher");
        saveUser(beta, UserRole.CENTRE_ADMIN, "admin@b.example.com", "Beta Admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(userAccountRepository.findByCentreId(alpha.getCentreId()))
                .extracting(UserAccount::getUserId)
                .containsExactly(alphaTeacher.getUserId());
        assertThat(userAccountRepository.findByCentreId(beta.getCentreId())).hasSize(2);

        // Batches seeded in BOTH centres: each centre sees exactly its own.
        GradeSubject gradeSubject = saveGradeSubject();
        Batch alphaBatch = new Batch();
        alphaBatch.setCentre(alpha);
        alphaBatch.setGradeSubject(gradeSubject);
        alphaBatch.setName("Alpha Batch");
        alphaBatch = batchRepository.save(alphaBatch);
        Batch betaBatch = new Batch();
        betaBatch.setCentre(beta);
        betaBatch.setGradeSubject(gradeSubject);
        betaBatch.setName("Beta Batch");
        betaBatch = batchRepository.save(betaBatch);
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRepository.findByCentreId(alpha.getCentreId()))
                .extracting(Batch::getBatchId)
                .containsExactly(alphaBatch.getBatchId());
        assertThat(batchRepository.findByCentreId(beta.getCentreId()))
                .extracting(Batch::getBatchId)
                .containsExactly(betaBatch.getBatchId());
        assertThat(centreRepository.existsByName("Alpha Tutoring")).isTrue();
        assertThat(centreRepository.existsByName("No Such Centre")).isFalse();
    }

    @Test
    void studentProfileSharesUserPrimaryKeyAndLinksGrade() {
        Centre centre = saveCentre("Alpha Tutoring");
        Grade grade = new Grade();
        grade.setCode("G8");
        grade.setName("Grade 8");
        grade = gradeRepository.save(grade);
        UserAccount student = saveUser(centre, UserRole.STUDENT, null, "Simran");
        // Students carry no password/email; staff defaults hold.
        assertThat(student.getPasswordHash()).isNull();
        assertThat(student.getStatus()).isEqualTo(UserStatus.ACTIVE);

        StudentProfile profile = new StudentProfile();
        profile.setUser(student);
        profile.setCentreId(centre.getCentreId());
        profile.setGrade(grade);
        profile.setFullName("Simran Kaur");
        studentProfileRepository.save(profile);

        entityManager.flush();
        entityManager.clear();

        StudentProfile reloaded = studentProfileRepository.findById(student.getUserId()).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(student.getUserId());
        assertThat(reloaded.getUser().getUserId()).isEqualTo(student.getUserId());
        assertThat(reloaded.getGrade().getCode()).isEqualTo("G8");
        assertThat(reloaded.getFullName()).isEqualTo("Simran Kaur");
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void masterDataFindersAndAuditColumns() {
        GradeSubject gradeSubject = saveGradeSubject();
        entityManager.flush();
        entityManager.clear();

        assertThat(gradeRepository.findByCode("G8")).isPresent();
        assertThat(gradeRepository.findByCode("G99")).isEmpty();
        assertThat(subjectRepository.findBySubjectName("Mathematics")).isPresent();
        assertThat(subjectRepository.findBySubjectName("Alchemy")).isEmpty();
        assertThat(
                        gradeSubjectRepository.findByGradeIdAndSubjectId(
                                gradeSubject.getGrade().getGradeId(),
                                gradeSubject.getSubject().getSubjectId()))
                .isPresent();
        // Auditing populates the NOT NULL audit timestamps on persist.
        Grade reloaded = gradeRepository.findByCode("G8").orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getCreatedBy()).isNull(); // no authenticated auditor until M2
        assertThat(reloaded.isActive()).isTrue();
    }
}
