package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** M5 Part 3: marking history, confirmation audit, and one-live-job enforcement. */
public class V17__MarkingQueue extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        try (Statement sql = connection.createStatement()) {
            sql.execute("ALTER TABLE background_job DROP CONSTRAINT uq_marking_attempt");
            if (postgres) {
                sql.execute("CREATE UNIQUE INDEX uq_marking_attempt_live ON background_job (question_attempt_id)"
                        + " WHERE status IN ('PENDING', 'RUNNING')");
            } else {
                // H2 has no partial indexes. This generated nullable key has identical
                // uniqueness semantics because terminal rows yield NULL and do not collide.
                sql.execute("ALTER TABLE background_job ADD COLUMN live_question_attempt_id uuid"
                        + " GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING', 'RUNNING')"
                        + " THEN question_attempt_id ELSE NULL END)");
                sql.execute("CREATE UNIQUE INDEX uq_marking_attempt_live ON background_job (live_question_attempt_id)");
            }
            sql.execute("ALTER TABLE question_attempts ADD COLUMN confirmed_breakdown jsonb");
            sql.execute("ALTER TABLE question_attempts ADD COLUMN marked_by uuid");
            sql.execute("ALTER TABLE question_attempts ADD COLUMN marked_at timestamp with time zone");
            sql.execute("ALTER TABLE question_attempts ADD CONSTRAINT fk_question_attempts_marker"
                    + " FOREIGN KEY (centre_id, marked_by) REFERENCES user_account (centre_id, user_id)");
        }
    }
}
