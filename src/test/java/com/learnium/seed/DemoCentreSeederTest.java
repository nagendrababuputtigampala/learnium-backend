package com.learnium.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.StudentProfileRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the real migrated schema (migration profile) with the demo
 * profile on, so the seeder executes at startup exactly as it would on the
 * demo deployment.
 */
@SpringBootTest
@ActiveProfiles({"migration", "demo"})
@Transactional
class DemoCentreSeederTest {

    @Autowired private DemoCentreSeeder seeder;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private BatchMembershipRepository membershipRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Centre demoCentre() {
        return centreRepository.findAll().stream()
                .filter(c -> c.getName().equals(DemoCentreSeeder.CENTRE_NAME))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void seedsTheMockStory() {
        Centre centre = demoCentre();
        List<UserAccount> users = userAccountRepository.findByCentreId(centre.getCentreId());
        assertThat(users).hasSize(7); // admin + teacher + 5 students

        UserAccount admin =
                users.stream().filter(u -> u.getRole() == UserRole.CENTRE_ADMIN).findFirst().orElseThrow();
        assertThat(admin.getEmail()).isEqualTo("admin@demo.learnium.local");
        assertThat(passwordEncoder.matches("Demo@123", admin.getPasswordHash())).isTrue();

        UserAccount teacher =
                users.stream().filter(u -> u.getRole() == UserRole.TEACHER).findFirst().orElseThrow();
        assertThat(teacher.getEmail()).isEqualTo("patel@demo.learnium.local");
        assertThat(teacher.getDisplayName()).isEqualTo("Mrs. Patel");
        assertThat(passwordEncoder.matches("Demo@123", teacher.getPasswordHash())).isTrue();

        List<Batch> batches = batchRepository.findByCentreId(centre.getCentreId());
        assertThat(batches).hasSize(1);
        Batch batch = batches.get(0);
        assertThat(batch.getName()).isEqualTo("Grade 8 Math — Batch B");
        assertThat(batch.getGradeSubject().getGrade().getCode()).isEqualTo("G8");
        assertThat(batch.getGradeSubject().getSubject().getSubjectName()).isEqualTo("Mathematics");
        assertThat(
                        topicRepository.findByGradeSubjectIdAndTopicName(
                                batch.getGradeSubject().getGradeSubjectId(), "Fractions"))
                .isPresent();

        List<BatchMembership> roster =
                membershipRepository.findByBatchIdAndStatus(batch.getBatchId(), MembershipStatus.ACTIVE);
        assertThat(roster).hasSize(6);
        BatchMembership teacherMembership =
                roster.stream()
                        .filter(m -> m.getMemberRole() == MemberRole.TEACHER)
                        .findFirst()
                        .orElseThrow();
        assertThat(teacherMembership.getUser().getUserId()).isEqualTo(teacher.getUserId());
        assertThat(teacherMembership.getPinHash()).isNull();

        List<BatchMembership> students =
                roster.stream().filter(m -> m.getMemberRole() == MemberRole.STUDENT).toList();
        assertThat(students).hasSize(5);
        assertThat(students)
                .extracting(m -> m.getUser().getDisplayName())
                .contains("Simran Kaur", "Arjun Singh", "Priya Sharma", "Rohan Mehta", "Ananya Iyer");
        for (BatchMembership student : students) {
            assertThat(passwordEncoder.matches("1234", student.getPinHash())).isTrue();
            assertThat(student.getUser().getEmail()).isNull();
            assertThat(student.getUser().getPasswordHash()).isNull();
        }
        assertThat(studentProfileRepository.findAll()).hasSize(5);
        assertThat(studentProfileRepository.findAll())
                .allSatisfy(p -> assertThat(p.getGrade().getCode()).isEqualTo("G8"));
    }

    @Test
    void secondRunIsIdempotent() {
        Centre centre = demoCentre();
        seeder.run(null);
        seeder.run(null);
        assertThat(userAccountRepository.findByCentreId(centre.getCentreId())).hasSize(7);
        Batch batch = batchRepository.findByCentreId(centre.getCentreId()).get(0);
        assertThat(
                        membershipRepository.findByBatchIdAndStatus(
                                batch.getBatchId(), MembershipStatus.ACTIVE))
                .hasSize(6);
    }
}
