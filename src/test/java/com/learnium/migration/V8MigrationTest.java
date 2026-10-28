package com.learnium.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import db.migration.V8__GlobalStaffEmailUniqueness;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The V8 DDL differs per database in exactly one keyword (PostgreSQL
 * generated columns require STORED, H2 rejects it). These tests pin the
 * statement set emitted for each vendor; the H2 variant is additionally
 * proven end-to-end by {@link MigrationTest}.
 */
class V8MigrationTest {

    private List<String> runMigrationFor(String databaseProductName) throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(databaseProductName);
        when(connection.createStatement()).thenReturn(statement);

        new V8__GlobalStaffEmailUniqueness().migrate(context);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, org.mockito.Mockito.times(4)).execute(sql.capture());
        return new ArrayList<>(sql.getAllValues());
    }

    @Test
    void postgresqlVariantStoresTheGeneratedColumn() throws Exception {
        List<String> sql = runMigrationFor("PostgreSQL");

        assertThat(sql.get(0)).contains("lower(email)");
        assertThat(sql.get(1)).contains("DROP CONSTRAINT uq_user_account_centre_email");
        assertThat(sql.get(2)).contains("GENERATED ALWAYS AS (lower(email)) STORED");
        assertThat(sql.get(3)).contains("uq_user_account_email_global");
    }

    @Test
    void h2VariantOmitsStored() throws Exception {
        List<String> sql = runMigrationFor("H2");

        assertThat(sql.get(2)).contains("GENERATED ALWAYS AS (lower(email))");
        assertThat(sql.get(2)).doesNotContain("STORED");
        assertThat(sql.get(3)).contains("uq_user_account_email_global");
    }
}
