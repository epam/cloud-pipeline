/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

/**
 * Wraps methods to operate in read-only mode and print the DB queries.
 */
@Service
@Slf4j
public class JdbcTemplateDryRunWrapper extends NamedParameterJdbcTemplate {

    public JdbcTemplateDryRunWrapper(final DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public <T> List<T> query(final String sql, final SqlParameterSource paramSource, final RowMapper<T> rowMapper)
            throws DataAccessException {
        final PreparedStatementCreator psc = getPreparedStatementCreator(sql, paramSource);
        logQuery(psc);
        return getJdbcOperations().query(psc, rowMapper);
    }

    @Override
    public int update(final String sql, final SqlParameterSource paramSource) throws DataAccessException {
        final PreparedStatementCreator psc = getPreparedStatementCreator(sql, paramSource);
        log.debug("Skipping update operation for query:");
        logQuery(psc);
        return 0;
    }

    private void logQuery(final PreparedStatementCreator sqlProvider) {
        if (sqlProvider instanceof SqlProvider) {
            log.debug(sqlProvider.toString());
        }
    }
}
