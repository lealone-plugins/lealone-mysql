/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.mysql;

import org.junit.Test;

public class MySQLSyntaxTest extends MySQLTestBase {
    @Test
    public void run() throws Exception {
        testTransactionIsolation();
        testBinaryColumn();
        testDatabaseStatement();
        testCharset();
        testComment();
    }

    void testTransactionIsolation() throws Exception {
        executeUpdate("SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED");
        executeQuery("select @@session.tx_isolation");
        assertEquals("READ-COMMITTED", getStringValue(1));
    }

    void testBinaryColumn() throws Exception {
        executeUpdate("drop table if exists varcharbinary");
        executeUpdate("CREATE TABLE varcharbinary (pk varchar(100) BINARY NOT NULL PRIMARY KEY, " + //
                "f1 varchar(100), f2 varchar(100), f3 int, f4 BINARY(1000))");
    }

    void testDatabaseStatement() throws Exception {
        executeUpdate("drop database if exists mysql_db1");
        executeUpdate("create database if not exists mysql_db1 "
                + "default character set utf8mb4 collate = utf8mb4_general_ci encryption = 'y'");
        executeUpdate("alter database mysql_db1 "
                + "default character set utf8mb4 default encryption = 'n' READ ONLY DEFAULT");
    }

    void testCharset() throws Exception {
        executeUpdate("create table if not exists charset_test (f1 int) "
                + "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
    }

    void testComment() throws Exception {
        executeUpdate("create table if not exists comment_test (f1 int) comment='comment test'");
    }
}
