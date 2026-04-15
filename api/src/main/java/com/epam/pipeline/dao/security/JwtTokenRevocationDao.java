/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.security;

import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public class JwtTokenRevocationDao extends NamedParameterJdbcDaoSupport {

    private String upsertRevocationQuery;
    private String existsByJtiQuery;
    private String insertRevocationsForUserQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertRevocation(final String jti, final LocalDateTime revokedAt) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.JTI.name(), jti);
        params.addValue(Parameters.REVOKED_AT.name(), DateUtils.convertLocalDateTimeToDate(revokedAt));
        getNamedParameterJdbcTemplate().update(upsertRevocationQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertRevocationsForAllNamedTokensOfUser(final Long userId, final LocalDateTime revokedAt) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.USER_ID.name(), userId);
        params.addValue(Parameters.REVOKED_AT.name(), DateUtils.convertLocalDateTimeToDate(revokedAt));
        getNamedParameterJdbcTemplate().update(insertRevocationsForUserQuery, params);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean isRevoked(final String jti) {
        final Boolean found = getJdbcTemplate().queryForObject(existsByJtiQuery, Boolean.class, jti);
        return Boolean.TRUE.equals(found);
    }

    enum Parameters {
        JTI,
        USER_ID,
        REVOKED_AT
    }

    @Required
    public void setUpsertRevocationQuery(final String upsertRevocationQuery) {
        this.upsertRevocationQuery = upsertRevocationQuery;
    }

    @Required
    public void setExistsByJtiQuery(final String existsByJtiQuery) {
        this.existsByJtiQuery = existsByJtiQuery;
    }

    @Required
    public void setInsertRevocationsForUserQuery(final String insertRevocationsForUserQuery) {
        this.insertRevocationsForUserQuery = insertRevocationsForUserQuery;
    }
}
