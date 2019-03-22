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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class DocumentGenerationPropertyDao extends NamedParameterJdbcDaoSupport {

    private String createDocumentGenerationPropertyQuery;
    private String updateDocumentGenerationPropertyQuery;
    private String deleteDocumentGenerationPropertyQuery;
    private String loadDocumentGenerationPropertyQuery;
    private String loadAllDocumentGenerationPropertiesQuery;
    private String loadDocumentGenerationPropertiesByPipelineIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createProperty(DocumentGenerationProperty property) {
        getNamedParameterJdbcTemplate().update(createDocumentGenerationPropertyQuery,
                DocumentGenerationPropertyParameters.getParameters(property));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateProperty(DocumentGenerationProperty property) {
        getNamedParameterJdbcTemplate().update(updateDocumentGenerationPropertyQuery,
                DocumentGenerationPropertyParameters.getParameters(property));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteProperty(DocumentGenerationProperty property) {
        getNamedParameterJdbcTemplate().update(deleteDocumentGenerationPropertyQuery,
                DocumentGenerationPropertyParameters.getParameters(property));
    }

    public DocumentGenerationProperty loadProperty(String name, Long id) {
        List<DocumentGenerationProperty> items = getJdbcTemplate()
                .query(loadDocumentGenerationPropertyQuery,
                DocumentGenerationPropertyParameters.getRowMapper(), name, id);

        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<DocumentGenerationProperty> loadAllProperties() {
        return getNamedParameterJdbcTemplate().query(loadAllDocumentGenerationPropertiesQuery,
                DocumentGenerationPropertyParameters.getRowMapper());
    }

    public List<DocumentGenerationProperty> loadAllPipelineProperties(Long pipelineId) {
        return getJdbcTemplate().query(loadDocumentGenerationPropertiesByPipelineIdQuery,
                DocumentGenerationPropertyParameters.getRowMapper(), pipelineId);
    }

    enum DocumentGenerationPropertyParameters {
        PROPERTY_NAME,
        PROPERTY_VALUE,
        PIPELINE_ID;

        static MapSqlParameterSource getParameters(DocumentGenerationProperty property) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(PIPELINE_ID.name(), property.getPipelineId());
            params.addValue(PROPERTY_NAME.name(), property.getPropertyName());
            params.addValue(PROPERTY_VALUE.name(), property.getPropertyValue());
            return params;
        }

        static RowMapper<DocumentGenerationProperty> getRowMapper() {
            return (rs, rowNum) -> {
                DocumentGenerationProperty property = new DocumentGenerationProperty();
                property.setPipelineId(rs.getLong(PIPELINE_ID.name()));
                property.setPropertyName(rs.getString(PROPERTY_NAME.name()));
                property.setPropertyValue(rs.getString(PROPERTY_VALUE.name()));
                return property;
            };
        }
    }

    @Required
    public void setCreateDocumentGenerationPropertyQuery(String query) {
        this.createDocumentGenerationPropertyQuery = query;
    }

    @Required
    public void setUpdateDocumentGenerationPropertyQuery(String query) {
        this.updateDocumentGenerationPropertyQuery = query;
    }

    @Required
    public void setDeleteDocumentGenerationPropertyQuery(String query) {
        this.deleteDocumentGenerationPropertyQuery = query;
    }

    @Required
    public void setLoadDocumentGenerationPropertyQuery(String query) {
        this.loadDocumentGenerationPropertyQuery = query;
    }

    @Required
    public void setLoadAllDocumentGenerationPropertiesQuery(String query) {
        this.loadAllDocumentGenerationPropertiesQuery = query;
    }

    @Required
    public void setLoadDocumentGenerationPropertiesByPipelineIdQuery(String query) {
        this.loadDocumentGenerationPropertiesByPipelineIdQuery = query;
    }
}
