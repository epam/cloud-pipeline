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

package com.epam.pipeline.dao.cluster.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtensionStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Persistence layer for {@link CapacityBlockExtension} - one row per extension request, several per
 * {@code capacity_reservation} row. Locking is the caller's: every write here is reached only after the caller has
 * already taken the lock on the reservation's node pool, through
 * {@code CapacityReservationService#loadForUpdate}, which is what serializes two extension requests for the same
 * reservation - there is nothing further to lock on this table itself.
 */
@RequiredArgsConstructor
public class CapacityBlockExtensionDao extends NamedParameterJdbcDaoSupport {

    private final String insertCapacityBlockExtensionQuery;
    private final String updateCapacityBlockExtensionQuery;
    private final String loadCapacityBlockExtensionQuery;
    private final String loadCapacityBlockExtensionsByReservationQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityBlockExtension create(final CapacityBlockExtension extension) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(insertCapacityBlockExtensionQuery, Parameters.getParameters(extension), keyHolder,
                        new String[]{"id"});
        extension.setId(keyHolder.getKey().longValue());
        return extension;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityBlockExtension update(final CapacityBlockExtension extension) {
        getNamedParameterJdbcTemplate()
                .update(updateCapacityBlockExtensionQuery, Parameters.getParameters(extension));
        return extension;
    }

    public Optional<CapacityBlockExtension> find(final Long id) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                        .query(loadCapacityBlockExtensionQuery, Parameters.getRowMapper(), id))
                .stream()
                .findFirst();
    }

    public List<CapacityBlockExtension> loadByReservationId(final Long reservationId) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                .query(loadCapacityBlockExtensionsByReservationQuery, Parameters.getRowMapper(), reservationId));
    }

    enum Parameters {
        ID,
        RESERVATION_ID,
        ORIGIN_ID,
        EXTENSION_OFFERING_ID,
        DURATION_HOURS,
        UPFRONT_FEE_AMOUNT,
        UPFRONT_FEE_CURRENCY,
        STATUS,
        STATUS_REASON,
        REQUESTED_BY,
        CREATED,
        UPDATED,
        START_DATE,
        END_DATE;

        static MapSqlParameterSource getParameters(final CapacityBlockExtension extension) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), extension.getId());
            params.addValue(RESERVATION_ID.name(), extension.getReservationId());
            params.addValue(ORIGIN_ID.name(), extension.getOriginId());
            params.addValue(EXTENSION_OFFERING_ID.name(), extension.getExtensionOfferingId());
            params.addValue(DURATION_HOURS.name(), extension.getDurationHours());
            params.addValue(UPFRONT_FEE_AMOUNT.name(), extension.getUpfrontFeeAmount());
            params.addValue(UPFRONT_FEE_CURRENCY.name(), extension.getUpfrontFeeCurrency());
            params.addValue(STATUS.name(), name(extension.getStatus()));
            params.addValue(STATUS_REASON.name(), extension.getStatusReason());
            params.addValue(REQUESTED_BY.name(), extension.getRequestedBy());
            params.addValue(CREATED.name(), timestamp(extension.getCreated()));
            params.addValue(UPDATED.name(), timestamp(extension.getUpdated()));
            params.addValue(START_DATE.name(), timestamp(extension.getStartDate()));
            params.addValue(END_DATE.name(), timestamp(extension.getEndDate()));
            return params;
        }

        static RowMapper<CapacityBlockExtension> getRowMapper() {
            return (rs, rowNum) -> {
                final CapacityBlockExtension extension = new CapacityBlockExtension();
                extension.setId(rs.getLong(ID.name()));
                extension.setReservationId(rs.getLong(RESERVATION_ID.name()));
                applyLong(rs, ORIGIN_ID.name(), extension::setOriginId);
                extension.setExtensionOfferingId(rs.getString(EXTENSION_OFFERING_ID.name()));
                extension.setDurationHours(rs.getInt(DURATION_HOURS.name()));
                extension.setUpfrontFeeAmount(rs.getBigDecimal(UPFRONT_FEE_AMOUNT.name()));
                extension.setUpfrontFeeCurrency(rs.getString(UPFRONT_FEE_CURRENCY.name()));
                extension.setStatus(CapacityBlockExtensionStatus.valueOf(rs.getString(STATUS.name())));
                extension.setStatusReason(rs.getString(STATUS_REASON.name()));
                extension.setRequestedBy(rs.getString(REQUESTED_BY.name()));
                extension.setCreated(dateTime(rs, CREATED.name()));
                extension.setUpdated(dateTime(rs, UPDATED.name()));
                extension.setStartDate(dateTime(rs, START_DATE.name()));
                extension.setEndDate(dateTime(rs, END_DATE.name()));
                return extension;
            };
        }

        private static String name(final Enum<?> value) {
            return Optional.ofNullable(value).map(Enum::name).orElse(null);
        }

        private static Timestamp timestamp(final LocalDateTime value) {
            return Optional.ofNullable(value).map(Timestamp::valueOf).orElse(null);
        }

        private static LocalDateTime dateTime(final ResultSet rs, final String field) throws SQLException {
            return Optional.ofNullable(rs.getTimestamp(field)).map(Timestamp::toLocalDateTime).orElse(null);
        }

        private static void applyLong(final ResultSet rs, final String field,
                                       final Consumer<Long> setter) throws SQLException {
            final long value = rs.getLong(field);
            if (!rs.wasNull()) {
                setter.accept(value);
            }
        }
    }
}
