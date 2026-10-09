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

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.region.CloudProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class CapacityReservationDao extends NamedParameterJdbcDaoSupport {

    private final String insertCapacityReservationQuery;
    private final String updateCapacityReservationQuery;
    private final String deleteCapacityReservationQuery;
    private final String loadCapacityReservationQuery;
    private final String loadCapacityReservationByNodePoolQuery;
    private final String loadCapacityReservationsByNodePoolsQuery;
    private final String loadAllCapacityReservationsQuery;
    private final String loadCapacityReservationsByStatusQuery;
    private final String loadCapacityReservationsByOwnerQuery;
    private final String updateCapacityReservationRetryQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation create(final CapacityReservation reservation) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(insertCapacityReservationQuery,
                        Parameters.getParameters(reservation),
                        keyHolder,
                        new String[]{"id"});
        reservation.setId(keyHolder.getKey().longValue());
        return reservation;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation update(final CapacityReservation reservation) {
        getNamedParameterJdbcTemplate()
                .update(updateCapacityReservationQuery, Parameters.getParameters(reservation));
        return reservation;
    }

    /**
     * Writes the retry fields - reason, requested dates and zone - only while the reservation is still in the status
     * the caller's copy shows. Returns whether it was written.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateRetry(final CapacityReservation reservation) {
        return getNamedParameterJdbcTemplate()
                .update(updateCapacityReservationRetryQuery, Parameters.getParameters(reservation)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long id) {
        getJdbcTemplate().update(deleteCapacityReservationQuery, id);
    }

    public Optional<CapacityReservation> find(final Long id) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                        .query(loadCapacityReservationQuery, Parameters.getRowMapper(), id))
                .stream()
                .findFirst();
    }

    public Optional<CapacityReservation> findByNodePoolId(final Long nodePoolId) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                        .query(loadCapacityReservationByNodePoolQuery, Parameters.getRowMapper(), nodePoolId))
                .stream()
                .findFirst();
    }

    public List<CapacityReservation> loadByNodePoolIds(final Collection<Long> nodePoolIds) {
        if (CollectionUtils.isEmpty(nodePoolIds)) {
            return Collections.emptyList();
        }
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(Parameters.NODE_POOL_IDS.name(), nodePoolIds);
        return ListUtils.emptyIfNull(getNamedParameterJdbcTemplate()
                .query(loadCapacityReservationsByNodePoolsQuery, params, Parameters.getRowMapper()));
    }

    public List<CapacityReservation> loadAll() {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                .query(loadAllCapacityReservationsQuery, Parameters.getRowMapper()));
    }

    public List<CapacityReservation> loadByStatus(final CapacityReservationStatus status) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                .query(loadCapacityReservationsByStatusQuery, Parameters.getRowMapper(), status.name()));
    }

    public List<CapacityReservation> loadByOwner(final String owner) {
        return ListUtils.emptyIfNull(getJdbcTemplate()
                .query(loadCapacityReservationsByOwnerQuery, Parameters.getRowMapper(), owner));
    }

    enum Parameters {
        ID,
        NODE_POOL_ID,
        NODE_POOL_IDS,
        ORIGIN_ID,
        NAME,
        REGION_ID,
        CLOUD_RESERVATION_ID,
        AVAILABILITY_ZONE,
        CLIENT_TOKEN,
        CLOUD_PROVIDER,
        RESERVATION_TYPE,
        STATUS,
        STATUS_REASON,
        OWNER,
        CREATED,
        UPDATED,
        INSTANCE_TYPE,
        INSTANCE_COUNT,
        REQUESTED_START_DATE,
        REQUESTED_END_DATE,
        START_DATE,
        END_DATE,
        COMMITMENT_DURATION,
        INSTANCE_PLATFORM,
        ATTEMPT,
        GRANTED_COMMITMENT_DURATION;

        static MapSqlParameterSource getParameters(final CapacityReservation reservation) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), reservation.getId());
            params.addValue(NODE_POOL_ID.name(), reservation.getNodePoolId());
            params.addValue(ORIGIN_ID.name(), reservation.getOriginId());
            params.addValue(NAME.name(), reservation.getName());
            params.addValue(REGION_ID.name(), reservation.getRegionId());
            params.addValue(CLOUD_RESERVATION_ID.name(), reservation.getCloudReservationId());
            params.addValue(AVAILABILITY_ZONE.name(), reservation.getAvailabilityZone());
            params.addValue(CLIENT_TOKEN.name(), reservation.getClientToken());
            params.addValue(CLOUD_PROVIDER.name(), name(reservation.getCloudProvider()));
            params.addValue(RESERVATION_TYPE.name(), name(reservation.getReservationType()));
            params.addValue(STATUS.name(), name(reservation.getStatus()));
            params.addValue(STATUS_REASON.name(), reservation.getStatusReason());
            params.addValue(OWNER.name(), reservation.getOwner());
            params.addValue(CREATED.name(), timestamp(reservation.getCreated()));
            params.addValue(UPDATED.name(), timestamp(reservation.getUpdated()));
            params.addValue(INSTANCE_TYPE.name(), reservation.getInstanceType());
            params.addValue(INSTANCE_COUNT.name(), reservation.getInstanceCount());
            params.addValue(REQUESTED_START_DATE.name(), timestamp(reservation.getRequestedStartDate()));
            params.addValue(REQUESTED_END_DATE.name(), timestamp(reservation.getRequestedEndDate()));
            params.addValue(START_DATE.name(), timestamp(reservation.getStartDate()));
            params.addValue(END_DATE.name(), timestamp(reservation.getEndDate()));
            params.addValue(COMMITMENT_DURATION.name(), reservation.getCommitmentDuration());
            params.addValue(INSTANCE_PLATFORM.name(),
                    Optional.ofNullable(reservation.getInstancePlatform())
                            .orElse(CapacityReservation.DEFAULT_INSTANCE_PLATFORM));
            params.addValue(ATTEMPT.name(), reservation.getAttempt());
            params.addValue(GRANTED_COMMITMENT_DURATION.name(),
                    reservation.getGrantedCommitmentDuration());
            return params;
        }

        static RowMapper<CapacityReservation> getRowMapper() {
            return (rs, rowNum) -> {
                final CapacityReservation reservation = new CapacityReservation();
                reservation.setId(rs.getLong(ID.name()));
                reservation.setNodePoolId(rs.getLong(NODE_POOL_ID.name()));
                applyLong(rs, ORIGIN_ID.name(), reservation::setOriginId);
                reservation.setName(rs.getString(NAME.name()));
                reservation.setRegionId(rs.getLong(REGION_ID.name()));
                reservation.setCloudReservationId(rs.getString(CLOUD_RESERVATION_ID.name()));
                reservation.setAvailabilityZone(rs.getString(AVAILABILITY_ZONE.name()));
                reservation.setClientToken(rs.getString(CLIENT_TOKEN.name()));
                reservation.setCloudProvider(CloudProvider.valueOf(rs.getString(CLOUD_PROVIDER.name())));
                reservation.setReservationType(
                        CapacityReservationType.valueOf(rs.getString(RESERVATION_TYPE.name())));
                reservation.setStatus(CapacityReservationStatus.valueOf(rs.getString(STATUS.name())));
                reservation.setStatusReason(rs.getString(STATUS_REASON.name()));
                reservation.setOwner(rs.getString(OWNER.name()));
                reservation.setCreated(dateTime(rs, CREATED.name()));
                reservation.setUpdated(dateTime(rs, UPDATED.name()));
                reservation.setInstanceType(rs.getString(INSTANCE_TYPE.name()));
                reservation.setInstanceCount(rs.getInt(INSTANCE_COUNT.name()));
                reservation.setRequestedStartDate(dateTime(rs, REQUESTED_START_DATE.name()));
                reservation.setRequestedEndDate(dateTime(rs, REQUESTED_END_DATE.name()));
                reservation.setStartDate(dateTime(rs, START_DATE.name()));
                reservation.setEndDate(dateTime(rs, END_DATE.name()));
                applyLong(rs, COMMITMENT_DURATION.name(), reservation::setCommitmentDuration);
                reservation.setInstancePlatform(rs.getString(INSTANCE_PLATFORM.name()));
                reservation.setAttempt(rs.getInt(ATTEMPT.name()));
                applyLong(rs, GRANTED_COMMITMENT_DURATION.name(),
                        reservation::setGrantedCommitmentDuration);
                return reservation;
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

        private static void applyLong(final ResultSet rs,
                                      final String field,
                                      final Consumer<Long> setter) throws SQLException {
            final long value = rs.getLong(field);
            if (!rs.wasNull()) {
                setter.accept(value);
            }
        }
    }
}
