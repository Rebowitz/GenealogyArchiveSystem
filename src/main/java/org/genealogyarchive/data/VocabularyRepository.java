package org.genealogyarchive.data;

import org.genealogyarchive.records.Vocabulary;
import org.genealogyarchive.records.VocabularyTerm;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

/** record_classification and record_role_type (V0021): permanent codes, correctable labels, retirement. */
public final class VocabularyRepository {

    private final JdbcClient jdbc;

    public VocabularyRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    private static String table(Vocabulary v) {
        return switch (v) {
            case CLASSIFICATION -> "gas.record_classification";
            case ROLE_TYPE -> "gas.record_role_type";
        };
    }

    public List<VocabularyTerm> list(Vocabulary v) {
        return jdbc.sql("SELECT code, label, description, retired, row_version FROM " + table(v) + " ORDER BY retired, label, code")
                .query((rs, n) -> new VocabularyTerm(v, rs.getString("code"), rs.getString("label"),
                        rs.getString("description"), rs.getBoolean("retired"), rs.getLong("row_version")))
                .list();
    }

    public Optional<VocabularyTerm> find(Vocabulary v, String code) {
        return jdbc.sql("SELECT code, label, description, retired, row_version FROM " + table(v) + " WHERE code = :c")
                .param("c", code)
                .query((rs, n) -> new VocabularyTerm(v, rs.getString("code"), rs.getString("label"),
                        rs.getString("description"), rs.getBoolean("retired"), rs.getLong("row_version")))
                .optional();
    }

    /** @return false if the code already exists */
    public boolean insert(Vocabulary v, String code, String label, String description) {
        return jdbc.sql("INSERT INTO " + table(v) + " (code, label, description) VALUES (:c, :l, :d) ON CONFLICT (code) DO NOTHING")
                .param("c", code).param("l", label).param("d", description).update() == 1;
    }

    /** @return false if the version changed meanwhile */
    public boolean update(Vocabulary v, String code, long expectedVersion, String label, String description, boolean retired) {
        return jdbc.sql("UPDATE " + table(v) + """
                 SET label = :l, description = :d, retired = :r, row_version = row_version + 1
                 WHERE code = :c AND row_version = :v
                """).param("l", label).param("d", description).param("r", retired).param("c", code)
                .param("v", expectedVersion).update() == 1;
    }
}
