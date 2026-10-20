package com.thanawat.flakesince;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesProjectTable() {
        jdbc.update("INSERT INTO project (name, slug) VALUES (?, ?)", "Flaky Demo", "flaky-demo");

        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM project WHERE slug = ?", Long.class, "flaky-demo");

        assertEquals(1L, count);
    }

    @Test
    void slugMustBeUnique() {
        String insert = "INSERT INTO project (name, slug) VALUES (?, ?)";
        jdbc.update(insert, "First", "same-slug");

        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update(insert, "Second", "same-slug"));
    }
}