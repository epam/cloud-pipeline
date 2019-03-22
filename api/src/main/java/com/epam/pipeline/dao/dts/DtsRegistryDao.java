/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.dts;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.dao.DaoHelper.mapListToSqlArray;

public class DtsRegistryDao extends NamedParameterJdbcDaoSupport {
    @Autowired
    private DaoHelper daoHelper;

    private String dtsRegistrySequence;
    private String loadAllDtsRegistriesQuery;
    private String loadDtsRegistryQuery;
    private String createDtsRegistryQuery;
    private String updateDtsRegistryQuery;
    private String deleteDtsRegistryQuery;

    public List<DtsRegistry> loadAll() {
        return getJdbcTemplate().query(loadAllDtsRegistriesQuery, DtsRegistryParameters.getRowMapper());
    }

    public Optional<DtsRegistry> loadById(Long registryId) {
        return getJdbcTemplate().query(loadDtsRegistryQuery, DtsRegistryParameters.getRowMapper(), registryId)
                .stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createRegistryId() {
        return daoHelper.createId(dtsRegistrySequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DtsRegistry create(DtsRegistry dtsRegistry) {
        dtsRegistry.setId(createRegistryId());
        dtsRegistry.setCreatedDate(DateUtils.now());
        getNamedParameterJdbcTemplate().update(createDtsRegistryQuery,
                DtsRegistryParameters.getParameters(dtsRegistry, getConnection()));
        return dtsRegistry;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(DtsRegistry dtsRegistry) {
        getNamedParameterJdbcTemplate().update(updateDtsRegistryQuery,
                DtsRegistryParameters.getParameters(dtsRegistry, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Long registryId) {
        getJdbcTemplate().update(deleteDtsRegistryQuery, registryId);
    }

    @Required
    public void setDtsRegistrySequence(String dtsRegistrySequence) {
        this.dtsRegistrySequence = dtsRegistrySequence;
    }

    @Required
    public void setLoadAllDtsRegistriesQuery(String loadAllDtsRegistriesQuery) {
        this.loadAllDtsRegistriesQuery = loadAllDtsRegistriesQuery;
    }

    @Required
    public void setLoadDtsRegistryQuery(String loadDtsRegistryQuery) {
        this.loadDtsRegistryQuery = loadDtsRegistryQuery;
    }

    @Required
    public void setCreateDtsRegistryQuery(String createDtsRegistryQuery) {
        this.createDtsRegistryQuery = createDtsRegistryQuery;
    }

    @Required
    public void setUpdateDtsRegistryQuery(String updateDtsRegistryQuery) {
        this.updateDtsRegistryQuery = updateDtsRegistryQuery;
    }

    @Required
    public void setDeleteDtsRegistryQuery(String deleteDtsRegistryQuery) {
        this.deleteDtsRegistryQuery = deleteDtsRegistryQuery;
    }

    enum DtsRegistryParameters {
        ID,
        NAME,
        SCHEDULABLE,
        CREATED_DATE,
        URL,
        PREFIXES;

        static MapSqlParameterSource getParameters(DtsRegistry dtsRegistry, Connection connection) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), dtsRegistry.getId());
            params.addValue(URL.name(), dtsRegistry.getUrl());
            params.addValue(NAME.name(), dtsRegistry.getName());
            params.addValue(CREATED_DATE.name(), dtsRegistry.getCreatedDate());
            params.addValue(SCHEDULABLE.name(), dtsRegistry.isSchedulable());
            Array prefixesArray = mapListToSqlArray(dtsRegistry.getPrefixes(), connection);
            params.addValue(PREFIXES.name(), prefixesArray);
            return params;
        }

        static RowMapper<DtsRegistry> getRowMapper() {
            return (rs, rowNum) -> {
                DtsRegistry dtsRegistry = new DtsRegistry();
                dtsRegistry.setId(rs.getLong(ID.name()));
                dtsRegistry.setUrl(rs.getString(URL.name()));
                dtsRegistry.setName(rs.getString(NAME.name()));
                dtsRegistry.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                dtsRegistry.setSchedulable(rs.getBoolean(SCHEDULABLE.name()));
                Array prefixesSqlArray = rs.getArray(PREFIXES.name());
                List<String> prefixesList = Arrays.asList((String[]) prefixesSqlArray.getArray());
                dtsRegistry.setPrefixes(prefixesList);
                return dtsRegistry;
            };
        }
    }
}
