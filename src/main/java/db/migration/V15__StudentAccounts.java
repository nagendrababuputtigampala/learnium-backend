package db.migration;

import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** M5 Part 1: centre-linked student accounts replace roster PIN sessions. */
public class V15__StudentAccounts extends BaseJavaMigration {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        try (Statement sql = connection.createStatement()) {
            sql.execute("ALTER TABLE centres ADD COLUMN code varchar(16)");
            sql.execute("ALTER TABLE user_account ADD COLUMN username varchar(64)");
            sql.execute("DROP INDEX uq_user_account_email_global");
            sql.execute("ALTER TABLE user_account DROP COLUMN email_normalized");
            sql.execute("ALTER TABLE user_account ADD COLUMN staff_email_normalized varchar(255)"
                    + " GENERATED ALWAYS AS (CASE WHEN role <> 'STUDENT' THEN lower(email) ELSE NULL END)"
                    + generatedColumnSuffix(postgres));
            sql.execute("CREATE UNIQUE INDEX uq_user_account_staff_email ON user_account (staff_email_normalized)");
        }
        backfillCentreCodes(connection);
        backfillStudentUsernames(connection);
        try (Statement sql = connection.createStatement()) {
            sql.execute("ALTER TABLE centres ALTER COLUMN code SET NOT NULL");
            sql.execute("ALTER TABLE centres ADD CONSTRAINT uq_centres_code UNIQUE (code)");
            sql.execute("ALTER TABLE user_account ADD CONSTRAINT uq_user_account_centre_username UNIQUE (centre_id, username)");
            sql.execute("ALTER TABLE user_account ADD CONSTRAINT ck_user_account_student_username"
                    + " CHECK ((role = 'STUDENT' AND username IS NOT NULL) OR (role <> 'STUDENT' AND username IS NULL))");
            sql.execute("CREATE INDEX idx_user_account_centre_username ON user_account (centre_id, username)");
            sql.execute("ALTER TABLE batch_memberships DROP COLUMN pin_hash");
            sql.execute("ALTER TABLE batch_memberships DROP COLUMN pin_failed_attempts");
            sql.execute("ALTER TABLE batch_memberships DROP COLUMN pin_locked");
        }
    }

    private void backfillCentreCodes(Connection connection) throws SQLException {
        Set<String> used = new HashSet<>();
        try (PreparedStatement read = connection.prepareStatement(
                        "SELECT centre_id, name FROM centres ORDER BY created_at, centre_id");
                ResultSet rows = read.executeQuery();
                PreparedStatement write = connection.prepareStatement(
                        "UPDATE centres SET code = ? WHERE centre_id = ?")) {
            while (rows.next()) {
                String base = centreCode(rows.getString("name"));
                String code = next(base, used, 16);
                used.add(code);
                write.setString(1, code);
                write.setObject(2, rows.getObject("centre_id"));
                write.addBatch();
            }
            write.executeBatch();
        }
    }

    private void backfillStudentUsernames(Connection connection) throws SQLException {
        Map<Object, Set<String>> used = new HashMap<>();
        try (PreparedStatement read = connection.prepareStatement(
                        "SELECT user_id, centre_id, display_name FROM user_account WHERE role = 'STUDENT' ORDER BY created_at, user_id");
                ResultSet rows = read.executeQuery();
                PreparedStatement write = connection.prepareStatement(
                        "UPDATE user_account SET username = ? WHERE user_id = ?")) {
            while (rows.next()) {
                Object centre = rows.getObject("centre_id");
                Set<String> centreNames = used.computeIfAbsent(centre, ignored -> new HashSet<>());
                String username = next(studentUsername(rows.getString("display_name")), centreNames, 30);
                centreNames.add(username);
                write.setString(1, username);
                write.setObject(2, rows.getObject("user_id"));
                write.addBatch();
            }
            write.executeBatch();
        }
    }

    public static String centreCode(String name) {
        String normalized = ascii(name);
        String[] words = NON_ALNUM.split(normalized);
        StringBuilder initials = new StringBuilder();
        for (String word : words) if (!word.isBlank()) initials.append(word.charAt(0));
        String candidate = initials.length() >= 2 ? initials.toString() : NON_ALNUM.matcher(normalized).replaceAll("");
        String value = candidate.isBlank() ? "centre" : candidate;
        return value.substring(0, Math.min(16, value.length()));
    }

    public static String studentUsername(String name) {
        String[] words = NON_ALNUM.split(ascii(name));
        List<String> parts = Arrays.stream(words).filter(word -> !word.isBlank()).toList();
        if (parts.isEmpty()) return "student";
        String base = parts.get(0);
        if (parts.size() > 1) base += "." + parts.get(parts.size() - 1).charAt(0);
        return base.substring(0, Math.min(30, base.length()));
    }

    public static String next(String base, Set<String> used, int limit) {
        if (!used.contains(base)) return base;
        int suffix = 2;
        while (true) {
            String number = Integer.toString(suffix);
            String candidate = base.substring(0, Math.min(base.length(), limit - number.length())) + number;
            if (!used.contains(candidate)) return candidate;
            suffix++;
        }
    }

    public static String generatedColumnSuffix(boolean postgres) { return postgres ? " STORED" : ""; }

    private static String ascii(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }
}
