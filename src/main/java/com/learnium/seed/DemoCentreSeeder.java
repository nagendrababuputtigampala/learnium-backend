package com.learnium.seed;

import com.learnium.model.content.Topic;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.StudentProfile;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.StudentProfileRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the demo centre from the product mock story (Mrs. Patel, Grade 8 Math —
 * Batch B, Fractions) so walk-in demos start from believable data. Runs only
 * under the {@code demo} profile and is idempotent: if the demo centre already
 * exists, it does nothing. Grade/Subject master rows come from the Flyway
 * migrations; everything centre-specific is created here.
 */
@Component
@Profile("demo")
public class DemoCentreSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCentreSeeder.class);

    static final String CENTRE_NAME = "Demo Tutoring Centre";
    static final String ADMIN_EMAIL = "admin@demo.learnium.local";
    static final String TEACHER_EMAIL = "patel@demo.learnium.local";
    static final String STAFF_PASSWORD = "Demo@123";
    static final String STUDENT_PIN = "1234";
    static final String BATCH_NAME = "Grade 8 Math — Batch B";
    static final List<String> STUDENT_NAMES =
            List.of("Simran Kaur", "Arjun Singh", "Priya Sharma", "Rohan Mehta", "Ananya Iyer");

    private final CentreRepository centreRepository;
    private final UserAccountRepository userAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BatchRepository batchRepository;
    private final BatchMembershipRepository membershipRepository;
    private final GradeRepository gradeRepository;
    private final SubjectRepository subjectRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final TopicRepository topicRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoCentreSeeder(
            CentreRepository centreRepository,
            UserAccountRepository userAccountRepository,
            StudentProfileRepository studentProfileRepository,
            BatchRepository batchRepository,
            BatchMembershipRepository membershipRepository,
            GradeRepository gradeRepository,
            SubjectRepository subjectRepository,
            GradeSubjectRepository gradeSubjectRepository,
            TopicRepository topicRepository,
            PasswordEncoder passwordEncoder) {
        this.centreRepository = centreRepository;
        this.userAccountRepository = userAccountRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.batchRepository = batchRepository;
        this.membershipRepository = membershipRepository;
        this.gradeRepository = gradeRepository;
        this.subjectRepository = subjectRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.topicRepository = topicRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (centreRepository.existsByName(CENTRE_NAME)) {
            log.info("Demo centre already seeded; skipping");
            return;
        }

        Centre centre = new Centre();
        centre.setName(CENTRE_NAME);
        centre.setContactEmail(ADMIN_EMAIL);
        centre = centreRepository.save(centre);

        String staffPasswordHash = passwordEncoder.encode(STAFF_PASSWORD);
        UserAccount admin = saveStaff(centre, UserRole.CENTRE_ADMIN, ADMIN_EMAIL, "Demo Admin", staffPasswordHash);
        UserAccount teacher =
                saveStaff(centre, UserRole.TEACHER, TEACHER_EMAIL, "Mrs. Patel", staffPasswordHash);
        log.info("Seeded demo centre {} with admin {}", centre.getCentreId(), admin.getEmail());

        Grade grade8 = gradeRepository.findByCode("G8").orElseThrow();
        Subject mathematics = subjectRepository.findBySubjectName("Mathematics").orElseThrow();
        // The pairing itself is master data, seeded by the V2 migration.
        GradeSubject gradeSubject =
                gradeSubjectRepository
                        .findByGradeIdAndSubjectId(grade8.getGradeId(), mathematics.getSubjectId())
                        .orElseThrow();

        Topic fractions = new Topic();
        fractions.setGradeSubject(gradeSubject);
        fractions.setTopicName("Fractions");
        topicRepository.save(fractions);

        Batch batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gradeSubject);
        batch.setName(BATCH_NAME);
        batch = batchRepository.save(batch);

        saveMembership(batch, teacher, MemberRole.TEACHER, null);
        String pinHash = passwordEncoder.encode(STUDENT_PIN);
        for (String studentName : STUDENT_NAMES) {
            UserAccount student = new UserAccount();
            student.setCentre(centre);
            student.setRole(UserRole.STUDENT);
            student.setDisplayName(studentName);
            student = userAccountRepository.save(student);

            StudentProfile profile = new StudentProfile();
            profile.setUser(student);
            profile.setCentreId(centre.getCentreId());
            profile.setGrade(grade8);
            profile.setFullName(studentName);
            studentProfileRepository.save(profile);

            saveMembership(batch, student, MemberRole.STUDENT, pinHash);
        }
        log.info("Seeded batch '{}' with {} students", BATCH_NAME, STUDENT_NAMES.size());
    }

    private UserAccount saveStaff(
            Centre centre, UserRole role, String email, String displayName, String passwordHash) {
        UserAccount user = new UserAccount();
        user.setCentre(centre);
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        return userAccountRepository.save(user);
    }

    private void saveMembership(Batch batch, UserAccount user, MemberRole role, String pinHash) {
        BatchMembership membership = new BatchMembership();
        membership.setBatch(batch);
        membership.setCentreId(batch.getCentre().getCentreId());
        membership.setUser(user);
        membership.setMemberRole(role);
        membership.setPinHash(pinHash);
        membershipRepository.save(membership);
    }
}
