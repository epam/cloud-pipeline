/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class NatGatewayDao extends NamedParameterJdbcDaoSupport {

    private final DaoHelper daoHelper;

    private String routingRuleSequence;
    private String createRouteQuery;
    private String loadSimilarRouteQuery;
    private String loadAllQueuedRoutesUpdateQuery;
    private String updateRouteQuery;
    private String deleteRouteQuery;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutingRules(final List<NatRoutingRuleDescription> newRouteRules,
                                               final NatRouteStatus status) {
        final List<NatRoute> newRoutes = newRouteRules.stream()
            .map(description -> mapRuleToRoute(description, status))
            .collect(Collectors.toList());
        return registerRoutes(newRoutes);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutes(final List<NatRoute> newRoutes) {
        final MapSqlParameterSource[] params = newRoutes.stream()
            .map(NatRoutingRuleParameters::getRouteParameters)
            .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(createRouteQuery, params);
        return newRoutes;
    }

    public List<NatRoute> loadQueuedRouteUpdates() {
        return getNamedParameterJdbcTemplate().query(loadAllQueuedRoutesUpdateQuery,
                                                     NatRoutingRuleParameters.getRowMapper());
    }

    public Optional<NatRoute> findRoute(final NatRoutingRuleDescription rule) {
        return getNamedParameterJdbcTemplate()
            .query(loadSimilarRouteQuery,
                   NatRoutingRuleParameters.getRouteParameters(mapRuleToRoute(rule, null), false),
                   NatRoutingRuleParameters.getRowMapper())
            .stream()
            .findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NatRoute updateRoute(final NatRoute route) {
        final NatRoute routeUpdate = route.toBuilder()
            .lastUpdateTime(getNowUTC())
            .build();
        getNamedParameterJdbcTemplate()
            .update(updateRouteQuery, NatRoutingRuleParameters.getRouteParameters(routeUpdate));
        return routeUpdate;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteRouteById(final Long routeId) {
        return getNamedParameterJdbcTemplate().update(deleteRouteQuery,
                                                      NatRoutingRuleParameters.getRouteIdAsParamSource(routeId)) > 0;
    }

    private LocalDateTime getNowUTC() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private NatRoute mapRuleToRoute(final NatRoutingRuleDescription ruleDescription, final NatRouteStatus status) {
        return NatRoute.builder()
            .routeId(daoHelper.createId(routingRuleSequence))
            .externalIp(ruleDescription.getExternalIp())
            .externalName(ruleDescription.getExternalName())
            .externalPort(ruleDescription.getPort())
            .protocol(ruleDescription.getProtocol())
            .lastUpdateTime(getNowUTC())
            .status(status)
            .description(ruleDescription.getDescription())
            .build();
    }

    @Required
    public void setRoutingRuleSequence(final String routingRuleSequence) {
        this.routingRuleSequence = routingRuleSequence;
    }

    @Required
    public void setCreateRouteQuery(final String createRouteQuery) {
        this.createRouteQuery = createRouteQuery;
    }

    @Required
    public void setLoadAllQueuedRoutesUpdateQuery(final String loadAllQueuedRoutesUpdateQuery) {
        this.loadAllQueuedRoutesUpdateQuery = loadAllQueuedRoutesUpdateQuery;
    }

    @Required
    public void setUpdateRouteQuery(final String updateRouteQuery) {
        this.updateRouteQuery = updateRouteQuery;
    }

    @Required
    public void setDeleteRouteQuery(final String deleteRouteQuery) {
        this.deleteRouteQuery = deleteRouteQuery;
    }

    @Required
    public void setLoadSimilarRouteQuery(final String loadSimilarRouteQuery) {
        this.loadSimilarRouteQuery = loadSimilarRouteQuery;
    }

    public enum NatRoutingRuleParameters {
        ROUTE_ID,
        EXTERNAL_NAME,
        EXTERNAL_IP,
        EXTERNAL_PORT,
        PROTOCOL,
        STATUS,
        DESCRIPTION,
        INTERNAL_NAME,
        INTERNAL_IP,
        INTERNAL_PORT,
        LAST_UPDATE_TIME,
        LAST_ERROR_TIME,
        LAST_ERROR_MESSAGE;

        static MapSqlParameterSource getRouteParameters(final NatRoute route) {
            return getRouteParameters(route, true);
        }

        static MapSqlParameterSource getRouteParameters(final NatRoute route, final boolean setStatus) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ROUTE_ID.name(), route.getRouteId());
            params.addValue(EXTERNAL_NAME.name(), route.getExternalName());
            params.addValue(EXTERNAL_IP.name(), route.getExternalIp());
            params.addValue(EXTERNAL_PORT.name(), route.getExternalPort());
            params.addValue(PROTOCOL.name(), route.getProtocol());
            params.addValue(INTERNAL_NAME.name(), route.getInternalName());
            params.addValue(INTERNAL_IP.name(), route.getInternalIp());
            params.addValue(LAST_ERROR_TIME.name(), route.getLastErrorTime());
            params.addValue(LAST_ERROR_MESSAGE.name(), route.getLastErrorMessage());
            params.addValue(INTERNAL_PORT.name(), route.getInternalPort());
            params.addValue(LAST_UPDATE_TIME.name(), route.getLastUpdateTime());
            params.addValue(DESCRIPTION.name(), route.getDescription());
            if (setStatus) {
                params.addValue(STATUS.name(), route.getStatus().name());
            }
            return params;
        }

        static MapSqlParameterSource getRouteIdAsParamSource(final Long routeId) {
            return new MapSqlParameterSource(NatRoutingRuleParameters.ROUTE_ID.name(), routeId);
        }

        static RowMapper<NatRoute> getRowMapper() {
            return (rs, rowNum) -> NatRoute.builder()
                .routeId(rs.getLong(ROUTE_ID.name()))
                .status(NatRouteStatus.valueOf(rs.getString(STATUS.name())))
                .externalName(rs.getString(EXTERNAL_NAME.name()))
                .externalIp(rs.getString(EXTERNAL_IP.name()))
                .externalPort(rs.getInt(EXTERNAL_PORT.name()))
                .protocol(rs.getString(PROTOCOL.name()))
                .internalName(rs.getString(INTERNAL_NAME.name()))
                .internalIp(rs.getString(INTERNAL_IP.name()))
                .internalPort(Optional.ofNullable(rs.getObject(INTERNAL_PORT.name()))
                                  .map(Integer.class::cast)
                                  .orElse(null))
                .lastUpdateTime(rs.getTimestamp(LAST_UPDATE_TIME.name()).toLocalDateTime())
                .lastErrorTime(Optional.ofNullable(rs.getTimestamp(LAST_ERROR_TIME.name()))
                                   .map(Timestamp::toLocalDateTime)
                                   .orElse(null))
                .lastErrorMessage(rs.getString(LAST_ERROR_MESSAGE.name()))
                .description(rs.getString(DESCRIPTION.name()))
                .build();
        }
    }
}
