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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class MetadataClassDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String metadataClassSequence;
    private String createMetadataClassQuery;
    private String updateMetadataClassExternalNameQuery;
    private String loadAllMetadataClassesQuery;
    private String loadMetadataClassQuery;
    private String deleteMetadataClassQuery;
    private String loadMetadataClassByNameQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createMetadataClass(MetadataClass metadataClass) {
        metadataClass.setId(daoHelper.createId(metadataClassSequence));
        getNamedParameterJdbcTemplate()
                .update(createMetadataClassQuery, MetadataClassParameters.getParameters(metadataClass));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMetadataClass(MetadataClass metadataClass) {
        getNamedParameterJdbcTemplate()
                .update(updateMetadataClassExternalNameQuery, MetadataClassParameters.getParameters(metadataClass));
    }

    public List<MetadataClass> loadAllMetadataClasses() {
        return getJdbcTemplate()
                .query(loadAllMetadataClassesQuery, MetadataClassParameters.getRowMapper());

    }

    public MetadataClass loadMetadataClass(Long id) {
        List<MetadataClass> items = getJdbcTemplate().query(loadMetadataClassQuery,
                MetadataClassParameters.getRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public MetadataClass loadMetadataClass(String name) {
        List<MetadataClass> items = getJdbcTemplate().query(loadMetadataClassByNameQuery,
                MetadataClassParameters.getRowMapper(), name);
        return !items.isEmpty() ? items.get(0) : null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataClass(Long classId) {
        getJdbcTemplate().update(deleteMetadataClassQuery, classId);
    }

    enum MetadataClassParameters {
        CLASS_ID,
        CLASS_NAME,
        EXTERNAL_CLASS_NAME;

        private static MapSqlParameterSource getParameters(MetadataClass metadataClass) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(CLASS_ID.name(), metadataClass.getId());
            params.addValue(CLASS_NAME.name(), metadataClass.getName());
            params.addValue(EXTERNAL_CLASS_NAME.name(), metadataClass.getFireCloudClassName() == null ? null
                    : metadataClass.getFireCloudClassName().name());
            return params;
        }

        private static RowMapper<MetadataClass> getRowMapper() {
            return (rs, rowNum) -> {
                MetadataClass metadataClass = new MetadataClass();
                metadataClass.setId(rs.getLong(CLASS_ID.name()));
                metadataClass.setName(rs.getString(CLASS_NAME.name()));
                String externalClassName = rs.getString(EXTERNAL_CLASS_NAME.name());
                if (!rs.wasNull()) {
                    metadataClass.setFireCloudClassName(FireCloudClass.valueOf(externalClassName));
                }
                return metadataClass;
            };
        }
    }

    @Required
    public void setLoadMetadataClassQuery(String loadMetadataClassQuery) {
        this.loadMetadataClassQuery = loadMetadataClassQuery;
    }

    @Required
    public void setMetadataClassSequence(String metadataClassSequence) {
        this.metadataClassSequence = metadataClassSequence;
    }

    @Required
    public void setCreateMetadataClassQuery(String createMetadataClassQuery) {
        this.createMetadataClassQuery = createMetadataClassQuery;
    }

    @Required
    public void setUpdateMetadataClassExternalNameQuery(String updateMetadataClassExternalNameQuery) {
        this.updateMetadataClassExternalNameQuery = updateMetadataClassExternalNameQuery;
    }

    @Required
    public void setLoadAllMetadataClassesQuery(String loadAllMetadataClassesQuery) {
        this.loadAllMetadataClassesQuery = loadAllMetadataClassesQuery;
    }

    @Required
    public void setDeleteMetadataClassQuery(String deleteMetadataClassQuery) {
        this.deleteMetadataClassQuery = deleteMetadataClassQuery;
    }

    @Required
    public void setLoadMetadataClassByNameQuery(String loadMetadataClassByNameQuery) {
        this.loadMetadataClassByNameQuery = loadMetadataClassByNameQuery;
    }
}
