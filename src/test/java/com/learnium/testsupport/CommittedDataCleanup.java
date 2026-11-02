package com.learnium.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Table cleanup for committed-state tests (classes running with
 * {@code Propagation.NOT_SUPPORTED}): every write the test made is really
 * committed, so independence comes from deleting the touched tables after
 * each test instead of rolling a wrapping transaction back. Deletes run in
 * foreign-key-safe order (children before parents) and are scoped to the
 * auth/tenancy tables these flows touch.
 */
public final class CommittedDataCleanup {

    private CommittedDataCleanup() {}

    public static void clearAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM learnium.questions");
        jdbcTemplate.execute("DELETE FROM learnium.background_job");
        jdbcTemplate.execute("DELETE FROM learnium.topics");
        jdbcTemplate.execute("DELETE FROM learnium.materials");
        jdbcTemplate.execute("DELETE FROM learnium.refresh_token");
        jdbcTemplate.execute("DELETE FROM learnium.account_token");
        jdbcTemplate.execute("DELETE FROM learnium.email_outbox");
        jdbcTemplate.execute("DELETE FROM learnium.batch_memberships");
        jdbcTemplate.execute("DELETE FROM learnium.student_profiles");
        jdbcTemplate.execute("DELETE FROM learnium.batches");
        jdbcTemplate.execute("DELETE FROM learnium.user_account");
        jdbcTemplate.execute("DELETE FROM learnium.centres");
        jdbcTemplate.execute("DELETE FROM learnium.grade_subjects");
        jdbcTemplate.execute("DELETE FROM learnium.grades");
        jdbcTemplate.execute("DELETE FROM learnium.subjects");
    }
}
