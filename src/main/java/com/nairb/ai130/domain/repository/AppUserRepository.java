package com.nairb.ai130.domain.repository;

import com.nairb.ai130.domain.entity.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AppUserRepository {

    private static final Logger log = LoggerFactory.getLogger(AppUserRepository.class);

    private final JdbcTemplate jdbc;

    public AppUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AppUser> ROW_MAPPER = (rs, rowNum) -> {
        AppUser u = new AppUser();
        u.setId(rs.getLong("id"));
        u.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return u;
    };

    public Optional<AppUser> findById(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM app_user WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public AppUser create() {
        jdbc.update("INSERT INTO app_user DEFAULT VALUES");
        Long id = jdbc.queryForObject("SELECT LASTVAL()", Long.class);
        AppUser u = new AppUser();
        u.setId(id);
        log.info("Created new user: id={}", id);
        return u;
    }
}
