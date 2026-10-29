package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.api.dto.MaterialDtos.CreateMaterialTextRequest;
import com.learnium.api.dto.MaterialDtos.MaterialDto;
import com.learnium.material.MaterialService;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedupe under real concurrency (F-3), against committed state: two
 * simultaneous identical submissions must produce exactly ONE material,
 * with both callers receiving it — the V11 unique index arbitrates, and
 * the loser recovers instead of erroring. Also pins the identity freeing
 * on soft delete and the F-8 identity rule (a shared capped prefix is not
 * the same chapter).
 *
 * <p>Runs on the {@code material-race} profile: the plain {@code test}
 * profile builds its schema from the entities, where migration-only
 * constraints (like V11's index, or V8's email index) do not exist — a
 * race test there would test nothing. The profile has its own database
 * because this suite's cleanup deletes whole tables.
 */
@SpringBootTest
@ActiveProfiles("material-race")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MaterialConcurrencyTest {

    private static final String PROSE =
            "Fractions represent parts of a whole. When the numerator and the denominator "
                    + "share a common factor, the fraction can be simplified by dividing both "
                    + "by that factor. Equivalent fractions name the same value in different "
                    + "forms, which is why one half equals two quarters and three sixths. ";

    @Autowired private MaterialService materialService;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount teacher;
    private GradeSubject gradeSubject;
    private LearniumPrincipal principal;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() {
        Centre centre = AuthFixtures.createCentre(centreRepository, "Race Centre");
        teacher =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                        "racer@race.test", "Rae Racer", "Passw0rd1", UserStatus.ACTIVE);
        // Distinct from the V2 master seeds, which also live in this
        // profile's Flyway-built schema.
        Grade grade = new Grade();
        grade.setCode("GR");
        grade.setName("Grade Race");
        grade = gradeRepository.save(grade);
        Subject subject = new Subject();
        subject.setSubjectName("Race Mathematics");
        subject = subjectRepository.save(subject);
        GradeSubject gs = new GradeSubject();
        gs.setGrade(grade);
        gs.setSubject(subject);
        gradeSubject = gradeSubjectRepository.save(gs);
        principal =
                LearniumPrincipal.staff(
                        teacher.getUserId(), centre.getCentreId(), UserRole.TEACHER);
    }

    @Test
    void simultaneousIdenticalPastesCreateExactlyOneMaterial() throws Exception {
        var request =
                new CreateMaterialTextRequest(
                        "Race chapter", gradeSubject.getGradeSubjectId(), PROSE.repeat(3));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<MaterialDto> submit =
                    () -> {
                        ready.countDown();
                        go.await();
                        return materialService.createFromText(principal, request);
                    };
            Future<MaterialDto> first = pool.submit(submit);
            Future<MaterialDto> second = pool.submit(submit);
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            MaterialDto a = first.get(60, TimeUnit.SECONDS);
            MaterialDto b = second.get(60, TimeUnit.SECONDS);

            // Both callers received the same material; exactly one of them
            // created it and the other got it as a dedupe hit — and the
            // database holds exactly one row.
            assertThat(a.materialId()).isEqualTo(b.materialId());
            assertThat(a.deduplicated()).isNotEqualTo(b.deduplicated());
            assertThat(materialRepository.findByCentreId(teacher.getCentre().getCentreId()))
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reuploadAfterSoftDeleteCreatesAFreshMaterial() {
        var request =
                new CreateMaterialTextRequest(
                        "Delete me", gradeSubject.getGradeSubjectId(), PROSE.repeat(3));

        MaterialDto first = materialService.createFromText(principal, request);
        materialService.delete(principal, first.materialId());
        MaterialDto second = materialService.createFromText(principal, request);

        // The soft delete freed the identity: the re-upload is a NEW
        // material, not a dedupe hit and not a uniqueness error.
        assertThat(second.materialId()).isNotEqualTo(first.materialId());
        assertThat(second.deduplicated()).isFalse();
        assertThat(materialRepository.findByCentreId(teacher.getCentre().getCentreId()))
                .hasSize(2);
        var deleted = materialRepository.findById(first.materialId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getDedupeHash()).isNull();
        // Provenance survives: the deleted row keeps its content hash.
        assertThat(deleted.getContentHash()).isNotNull();
    }

    @Test
    void chaptersSharingACappedPrefixDoNotDedupe() {
        // Both chapters share their first 210,000 characters — beyond the
        // 200,000 storage cap, so their STORED texts are identical — and
        // differ only afterwards. The full-text identity must keep them
        // apart (F-8).
        String prefix = "word ".repeat(42_000);
        MaterialDto first =
                materialService.createFromText(
                        principal,
                        new CreateMaterialTextRequest(
                                "Long chapter A",
                                gradeSubject.getGradeSubjectId(),
                                prefix + "alpha ".repeat(20)));
        MaterialDto second =
                materialService.createFromText(
                        principal,
                        new CreateMaterialTextRequest(
                                "Long chapter B",
                                gradeSubject.getGradeSubjectId(),
                                prefix + "beta ".repeat(20)));

        assertThat(first.extractionStatus()).isEqualTo("OVER_LIMIT");
        assertThat(second.extractionStatus()).isEqualTo("OVER_LIMIT");
        assertThat(second.materialId()).isNotEqualTo(first.materialId());
        assertThat(second.deduplicated()).isFalse();
        assertThat(second.contentHash()).isNotEqualTo(first.contentHash());
        assertThat(materialRepository.findByCentreId(teacher.getCentre().getCentreId()))
                .hasSize(2);
    }
}
