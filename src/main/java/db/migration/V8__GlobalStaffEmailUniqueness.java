package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V8: staff emails are globally unique, case-insensitively.
 *
 * <p>The login door resolves a staff account by email alone, so an email may
 * exist on at most one account across ALL centres. Until now the only
 * database guarantee was {@code UNIQUE (centre_id, email)} from V2, and
 * global uniqueness rested on a racy application-level pre-check (two
 * concurrent creations in different centres could both pass it). This
 * migration makes the database the backstop:
 *
 * <ol>
 *   <li>existing emails are normalised to lower case (logins and lookups
 *       normalise the same way in the service layer);
 *   <li>the per-centre constraint is replaced by a unique index over a
 *       generated lower-case column — the portable equivalent of an
 *       expression index on {@code lower(email)}. NULL emails (students)
 *       stay unrestricted: a unique index treats NULLs as distinct.
 * </ol>
 *
 * <p>It is a Java migration for exactly one reason: the generated-column
 * syntax differs between the two databases this codebase runs — PostgreSQL
 * requires the {@code STORED} keyword, H2 rejects it — and neither accepts
 * the other's expression-index syntax. The vendor is detected from the
 * connection, so the invariant (and the Flyway version) stays single and
 * identical everywhere. The plain {@code idx_user_account_email} index from
 * V2 is kept for lookups.
 */
public class V8__GlobalStaffEmailUniqueness extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean postgresql =
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE user_account SET email = lower(email)"
                            + " WHERE email IS NOT NULL AND email <> lower(email)");
            statement.execute("ALTER TABLE user_account DROP CONSTRAINT uq_user_account_centre_email");
            statement.execute(
                    "ALTER TABLE user_account ADD COLUMN email_normalized varchar(255)"
                            + " GENERATED ALWAYS AS (lower(email))"
                            + (postgresql ? " STORED" : ""));
            statement.execute(
                    "CREATE UNIQUE INDEX uq_user_account_email_global"
                            + " ON user_account (email_normalized)");
        }
    }
}
