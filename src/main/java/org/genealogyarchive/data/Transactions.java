package org.genealogyarchive.data;

import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** Transaction demarcation for services: one PostgreSQL transaction per service operation. */
public final class Transactions {

    private Transactions() {
    }

    public static TransactionTemplate template(DataSource dataSource) {
        return new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }
}
