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
import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@CacheConfig(cacheManager = "jwtTokenRevocationCacheManager")
public class JwtTokenRevocationDao extends NamedParameterJdbcDaoSupport {

    private String upsertRevocationQuery;
    private String existsByJtiQuery;

    /**
     * Persists revocation and updates the cache so {@link #isRevoked(String)} resolves without hitting the DB
     * on subsequent checks for this {@code jti}.
     */
    @CachePut(value = CacheConfiguration.JWT_TOKEN_REVOCATION_CACHE, key = "#jti")
    @Transactional(propagation = Propagation.MANDATORY)
    public Boolean upsertRevocation(final String jti, final LocalDateTime revokedAt) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.JTI.name(), jti);
        params.addValue(Parameters.REVOKED_AT.name(), DateUtils.convertLocalDateTimeToDate(revokedAt));
        getNamedParameterJdbcTemplate().update(upsertRevocationQuery, params);
        return Boolean.TRUE;
    }

    /**
     * Whether {@code jti} is present in the revocation table. Cached for fast path when using Redis
     * ({@code cache.type=REDIS}).
     */
    @Cacheable(value = CacheConfiguration.JWT_TOKEN_REVOCATION_CACHE, key = "#jti")
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean isRevoked(final String jti) {
        final Boolean found = getJdbcTemplate().queryForObject(existsByJtiQuery, Boolean.class, jti);
        return Boolean.TRUE.equals(found);
    }

    enum Parameters {
        JTI,
        REVOKED_AT
    }

    public void setUpsertRevocationQuery(final String upsertRevocationQuery) {
        this.upsertRevocationQuery = upsertRevocationQuery;
    }


    public void setExistsByJtiQuery(final String existsByJtiQuery) {
        this.existsByJtiQuery = existsByJtiQuery;
    }
}
