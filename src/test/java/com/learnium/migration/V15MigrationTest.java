package com.learnium.migration;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class V15MigrationTest {
    @Test void upgradesStudentsToCentreLinkedAccountsAndRetiresPins() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:student-upgrade-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).schemas("learnium").target("14").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        centre(jdbc, a, "Demo Tutoring Centre"); centre(jdbc, b, "Digital Training Centre");
        student(jdbc, s1, a, "Simran Kaur", "family@example.test");
        student(jdbc, s2, a, "Simran Khan", null);
        Flyway.configure().dataSource(source).schemas("learnium").load().migrate();
        assertThat(jdbc.queryForObject("select code from learnium.centres where centre_id = ?", String.class, a)).isEqualTo("dtc");
        assertThat(jdbc.queryForObject("select code from learnium.centres where centre_id = ?", String.class, b)).isEqualTo("dtc2");
        assertThat(jdbc.queryForList("select username from learnium.user_account order by created_at, user_id", String.class))
                .containsExactlyInAnyOrder("simran.k", "simran.k2");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema='learnium' and table_name='batch_memberships'", String.class))
                .doesNotContain("pin_hash", "pin_failed_attempts", "pin_locked");
        // Repeated student contact emails remain legal after staff-only uniqueness replaces V8.
        student(jdbc, UUID.randomUUID(), a, "Other Student", "family@example.test", "other.s");
    }

    @Test void namingHelpersAreStableForAccentsEmptyNamesAndLongCollisions() {
        assertThat(db.migration.V15__StudentAccounts.centreCode("École Alpha")).isEqualTo("ea");
        assertThat(db.migration.V15__StudentAccounts.centreCode("-Alpha")).isEqualTo("alpha");
        assertThat(db.migration.V15__StudentAccounts.centreCode(null)).isEqualTo("centre");
        assertThat(db.migration.V15__StudentAccounts.centreCode(" ")).isEqualTo("centre");
        assertThat(db.migration.V15__StudentAccounts.studentUsername("Ána María López")).isEqualTo("ana.l");
        assertThat(db.migration.V15__StudentAccounts.studentUsername("-Ana")).isEqualTo("ana");
        assertThat(db.migration.V15__StudentAccounts.studentUsername("Ana")).isEqualTo("ana");
        assertThat(db.migration.V15__StudentAccounts.studentUsername(" ")).isEqualTo("student");
        assertThat(db.migration.V15__StudentAccounts.generatedColumnSuffix(true)).isEqualTo(" STORED");
        assertThat(db.migration.V15__StudentAccounts.generatedColumnSuffix(false)).isEmpty();
        assertThat(db.migration.V15__StudentAccounts.next("student", Set.of(), 30)).isEqualTo("student");
        assertThat(db.migration.V15__StudentAccounts.next("student", Set.of("student", "student2"), 30)).isEqualTo("student3");
    }

    private void centre(JdbcTemplate jdbc, UUID id, String name) {
        jdbc.update("insert into learnium.centres (centre_id,name,created_at,updated_at) values (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, name);
    }
    private void student(JdbcTemplate jdbc, UUID id, UUID centre, String name, String email) {
        jdbc.update("insert into learnium.user_account (user_id,centre_id,role,email,display_name,created_at,updated_at) values (?,?,'STUDENT',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, centre, email, name);
    }
    private void student(JdbcTemplate jdbc, UUID id, UUID centre, String name, String email, String username) {
        jdbc.update("insert into learnium.user_account (user_id,centre_id,role,email,username,display_name,created_at,updated_at) values (?,?,'STUDENT',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, centre, email, username, name);
    }
}
