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

package com.epam.pipeline.dao.datastorage.rules;

import java.util.Date;
import java.util.List;

import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class DataStorageRuleDao extends NamedParameterJdbcDaoSupport {

    private String createDataStorageRuleQuery;
    private String deleteDataStorageRuleQuery;
    private String loadAllDataStorageRulesQuery;
    private String loadDataStorageRulesForPipelineQuery;
    private String loadDataStorageRuleQuery;
    private String deleteRulesByPipelineQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createDataStorageRule(DataStorageRule rule) {
        getNamedParameterJdbcTemplate().update(createDataStorageRuleQuery,
                DataStorageRulesParameters.getParameters(rule));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteDataStorageRule(Long pipelineId, String fileMask) {
        getJdbcTemplate().update(deleteDataStorageRuleQuery, pipelineId, fileMask.trim());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<DataStorageRule> loadAllDataStorageRules() {
        return getNamedParameterJdbcTemplate().query(loadAllDataStorageRulesQuery,
                DataStorageRulesParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<DataStorageRule> loadDataStorageRulesForPipeline(Long pipelineId) {
        return getJdbcTemplate().query(loadDataStorageRulesForPipelineQuery,
                DataStorageRulesParameters.getRowMapper(), pipelineId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DataStorageRule loadDataStorageRule(Long pipelineId, String fileMask) {
        List<DataStorageRule> items = getJdbcTemplate().query(loadDataStorageRuleQuery,
                DataStorageRulesParameters.getRowMapper(), pipelineId, fileMask.trim());
        return !items.isEmpty() ? items.get(0) : null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRulesByPipeline(Long id) {
        getJdbcTemplate().update(deleteRulesByPipelineQuery, id);
    }

    public enum DataStorageRulesParameters {
        PIPELINE_ID,
        FILE_MASK,
        MOVE_TO_STS,
        CREATED_DATE;

        static MapSqlParameterSource getParameters(DataStorageRule rule) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(PIPELINE_ID.name(), rule.getPipelineId());
            params.addValue(FILE_MASK.name(), rule.getFileMask().trim());
            params.addValue(MOVE_TO_STS.name(), rule.getMoveToSts());
            params.addValue(CREATED_DATE.name(), rule.getCreatedDate());
            return params;
        }

        static RowMapper<DataStorageRule> getRowMapper() {
            return (rs, rowNum) -> {
                DataStorageRule rule = new DataStorageRule();
                rule.setPipelineId(rs.getLong(PIPELINE_ID.name()));
                rule.setFileMask(rs.getString(FILE_MASK.name()));
                rule.setMoveToSts(rs.getBoolean(MOVE_TO_STS.name()));
                rule.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                return rule;
            };
        }
    }

    @Required
    public void setCreateDataStorageRuleQuery(String createDataStorageRuleQuery) {
        this.createDataStorageRuleQuery = createDataStorageRuleQuery;
    }

    @Required
    public void setDeleteDataStorageRuleQuery(String deleteDataStorageRuleQuery) {
        this.deleteDataStorageRuleQuery = deleteDataStorageRuleQuery;
    }

    @Required
    public void setLoadAllDataStorageRulesQuery(String loadAllDataStorageRulesQuery) {
        this.loadAllDataStorageRulesQuery = loadAllDataStorageRulesQuery;
    }

    @Required
    public void setLoadDataStorageRulesForPipelineQuery(String loadDataStorageRuleQuery) {
        this.loadDataStorageRulesForPipelineQuery = loadDataStorageRuleQuery;
    }

    @Required
    public void setLoadDataStorageRuleQuery(String loadDataStorageRuleQuery) {
        this.loadDataStorageRuleQuery = loadDataStorageRuleQuery;
    }

    public void setDeleteRulesByPipelineQuery(String deleteRulesByPipelineQuery) {
        this.deleteRulesByPipelineQuery = deleteRulesByPipelineQuery;
    }
}
