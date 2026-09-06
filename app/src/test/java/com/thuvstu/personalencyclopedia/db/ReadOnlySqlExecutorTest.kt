package com.thuvstu.personalencyclopedia.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadOnlySqlExecutorTest {

    @Test
    fun selectIsAllowed() {
        assertNull(ReadOnlySqlExecutor.denyReason("SELECT 1"))
        assertNull(ReadOnlySqlExecutor.denyReason("  select * from entry  "))
    }

    @Test
    fun withIsAllowed() {
        assertNull(
            ReadOnlySqlExecutor.denyReason("WITH x AS (SELECT 1 AS n) SELECT * FROM x")
        )
    }

    @Test
    fun blankIsDenied() {
        assertEquals("SQLが空です", ReadOnlySqlExecutor.denyReason("   "))
    }

    @Test
    fun writeStatementsAreDenied() {
        assertNotNull(ReadOnlySqlExecutor.denyReason("INSERT INTO entry (id) VALUES ('x')"))
        assertNotNull(ReadOnlySqlExecutor.denyReason("UPDATE entry SET title='x'"))
        assertNotNull(ReadOnlySqlExecutor.denyReason("DELETE FROM entry"))
        assertNotNull(ReadOnlySqlExecutor.denyReason("DROP TABLE entry"))
        assertNotNull(ReadOnlySqlExecutor.denyReason("PRAGMA wal_checkpoint"))
    }

    @Test
    fun hiddenWriteAfterSelectIsDenied() {
        assertNotNull(ReadOnlySqlExecutor.denyReason("SELECT 1; DELETE FROM entry"))
    }

    @Test
    fun writeKeywordInCommentIsIgnored() {
        assertNull(ReadOnlySqlExecutor.denyReason("SELECT 1 -- DROP TABLE entry"))
        assertNull(ReadOnlySqlExecutor.denyReason("SELECT 'DELETE' AS x"))
    }
}
