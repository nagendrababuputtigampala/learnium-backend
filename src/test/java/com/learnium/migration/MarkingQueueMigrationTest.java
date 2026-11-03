package com.learnium.migration;

import static org.mockito.Mockito.*;

import db.migration.V17__MarkingQueue;
import java.sql.*;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class MarkingQueueMigrationTest {
    @Test
    void createsTheNativePartialIndexOnPostgresql() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);

        new V17__MarkingQueue().migrate(context);

        verify(statement).execute("CREATE UNIQUE INDEX uq_marking_attempt_live ON background_job (question_attempt_id)"
                + " WHERE status IN ('PENDING', 'RUNNING')");
        verify(statement, never()).execute(contains("live_question_attempt_id"));
        verify(statement).close();
    }
}
