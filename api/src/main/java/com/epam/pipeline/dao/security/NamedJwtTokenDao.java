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

import com.epam.pipeline.app.CacheConfiguration;
import com.epam.pipeline.entity.security.NamedJwtToken;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@CacheConfig(cacheManager = "namedJwtTokenCacheManager")
public class NamedJwtTokenDao extends NamedParameterJdbcDaoSupport {

    private String insertTokenQuery;
    private String loadByJtiQuery;
    private String loadByUserIdQuery;
    private String deleteByJtiQuery;
    private String deleteByUserIdQuery;

    @CachePut(value = CacheConfiguration.NAMED_JWT_TOKEN_CACHE, key = "#token.jti")
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(final NamedJwtToken token) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(NamedJwtTokenParameters.JTI.name(), token.getJti());
        params.addValue(NamedJwtTokenParameters.USER_ID.name(), token.getUserId());
        params.addValue(NamedJwtTokenParameters.CREATED_BY.name(), token.getCreatedBy());
        params.addValue(NamedJwtTokenParameters.TOKEN_NAME.name(), token.getTokenName());
        params.addValue(NamedJwtTokenParameters.ISSUED_AT.name(),
                DateUtils.convertLocalDateTimeToDate(token.getIssuedAt()));
        params.addValue(NamedJwtTokenParameters.EXPIRES_AT.name(),
                DateUtils.convertLocalDateTimeToDate(token.getExpiresAt()));
        getNamedParameterJdbcTemplate().update(insertTokenQuery, params);
    }

    @Cacheable(value = CacheConfiguration.NAMED_JWT_TOKEN_CACHE, key = "#jti")
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<NamedJwtToken> loadByJti(final String jti) {
        final List<NamedJwtToken> list = getJdbcTemplate().query(loadByJtiQuery, getNamedTokenRowMapper(), jti);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @CacheEvict(value = CacheConfiguration.NAMED_JWT_TOKEN_CACHE, key = "#jti")
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteByJti(final String jti) {
        return getJdbcTemplate().update(deleteByJtiQuery, jti);
    }

    @CacheEvict(value = CacheConfiguration.NAMED_JWT_TOKEN_CACHE, allEntries = true)
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteByUserId(final Long userId) {
        return getJdbcTemplate().update(deleteByUserIdQuery, userId);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public List<NamedJwtToken> loadByUserId(final Long userId) {
        return getJdbcTemplate().query(loadByUserIdQuery, getNamedTokenRowMapper(), userId);
    }

    private RowMapper<NamedJwtToken> getNamedTokenRowMapper() {
        return (rs, rowNum) -> NamedJwtToken.builder()
                .jti(rs.getString(1))
                .userId(rs.getLong(2))
                .createdBy(rs.getLong(3))
                .tokenName(rs.getString(4))
                .issuedAt(toLdt(rs.getTimestamp(5)))
                .expiresAt(toLdt(rs.getTimestamp(6)))
                .token(null)
                .build();
    }

    private static LocalDateTime toLdt(final Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    enum NamedJwtTokenParameters {
        JTI,
        USER_ID,
        CREATED_BY,
        TOKEN_NAME,
        ISSUED_AT,
        EXPIRES_AT
    }

    @Required
    public void setInsertTokenQuery(final String insertTokenQuery) {
        this.insertTokenQuery = insertTokenQuery;
    }

    @Required
    public void setLoadByJtiQuery(final String loadByJtiQuery) {
        this.loadByJtiQuery = loadByJtiQuery;
    }

    @Required
    public void setLoadByUserIdQuery(final String loadByUserIdQuery) {
        this.loadByUserIdQuery = loadByUserIdQuery;
    }

    @Required
    public void setDeleteByJtiQuery(final String deleteByJtiQuery) {
        this.deleteByJtiQuery = deleteByJtiQuery;
    }

    @Required
    public void setDeleteByUserIdQuery(final String deleteByUserIdQuery) {
        this.deleteByUserIdQuery = deleteByUserIdQuery;
    }
}
