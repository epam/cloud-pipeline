/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.billing;

import com.epam.pipeline.entity.billing.BillingReportTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class BillingTemplateDao  extends NamedParameterJdbcDaoSupport {

    private String createBillingTemplateQuery;
    private String updateBillingTemplateQuery;
    private String deleteBillingTemplateQuery;
    private String loadAllBillingTemplateQuery;
    private String loadBillingTemplateByIdQuery;
    private String loadBillingTemplateByNameQuery;

    public List<BillingReportTemplate> loadAll() {
        return getJdbcTemplate().query(loadAllBillingTemplateQuery, BillingReportTemplateParameters.getRowMapper());
    }

    public Optional<BillingReportTemplate> load(final Long id) {
        return getJdbcTemplate().query(loadBillingTemplateByIdQuery, BillingReportTemplateParameters.getRowMapper(), id)
                .stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BillingReportTemplate create(final BillingReportTemplate template) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate().update(createBillingTemplateQuery,
                BillingReportTemplateParameters.getParameters(template), keyHolder, new String[] { "id" });
        template.setId(keyHolder.getKey().longValue());
        return template;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BillingReportTemplate update(final BillingReportTemplate template) {
        getNamedParameterJdbcTemplate().update(updateBillingTemplateQuery,
                BillingReportTemplateParameters.getParameters(template));
        return template;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long id) {
        getJdbcTemplate().update(deleteBillingTemplateQuery, id);
    }

    public Optional<BillingReportTemplate> loadByName(final String name) {
        return getJdbcTemplate().query(loadBillingTemplateByNameQuery, BillingReportTemplateParameters.getRowMapper(), name)
                .stream().findFirst();
    }

    enum BillingReportTemplateParameters {
        ID,
        NAME,
        DESCRIPTION,
        TEMPLATE,
        SETTINGS;

        static MapSqlParameterSource getParameters(final BillingReportTemplate template) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), template.getId());
            params.addValue(NAME.name(), template.getName());
            params.addValue(DESCRIPTION.name(), template.getDescription());
            params.addValue(TEMPLATE.name(), template.getTemplate());
            params.addValue(SETTINGS.name(), template.getSettings());
            return params;
        }

        static RowMapper<BillingReportTemplate> getRowMapper() {
            return (rs, rowNum) -> {
                BillingReportTemplate template = new BillingReportTemplate();
                template.setId(rs.getLong(ID.name()));
                template.setName(rs.getString(NAME.name()));
                template.setDescription(rs.getString(DESCRIPTION.name()));
                template.setTemplate(TEMPLATE.name());
                template.setSettings(SETTINGS.name());
                return template;
            };
        }
    }

    public void setCreateBillingTemplateQuery(final String createBillingTemplateQuery) {
        this.createBillingTemplateQuery = createBillingTemplateQuery;
    }

    public void setUpdateBillingTemplateQuery(final String updateBillingTemplateQuery) {
        this.updateBillingTemplateQuery = updateBillingTemplateQuery;
    }

    public void setDeleteBillingTemplateQuery(final String deleteBillingTemplateQuery) {
        this.deleteBillingTemplateQuery = deleteBillingTemplateQuery;
    }

    public void setLoadAllBillingTemplateQuery(final String loadAllBillingTemplateQuery) {
        this.loadAllBillingTemplateQuery = loadAllBillingTemplateQuery;
    }

    public void setLoadBillingTemplateByIdQuery(final String loadBillingTemplateByIdQuery) {
        this.loadBillingTemplateByIdQuery = loadBillingTemplateByIdQuery;
    }

    public void setLoadBillingTemplateByNameQuery(final String loadBillingTemplateByNameQuery) {
        this.loadBillingTemplateByNameQuery = loadBillingTemplateByNameQuery;
    }
}
