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

package com.epam.pipeline.dao.filter;


import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.filter.FilterExpression;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.manager.filter.converters.DateConverter;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class FilterDao extends NamedParameterJdbcDaoSupport {

    private Pattern wherePattern = Pattern.compile("@WHERE@");

    private String filterPipelineRunsBaseQuery;
    private String countFilteredPipelineRunsBaseQuery;

    public List<PipelineRun> filterPipelineRuns(FilterExpression filterExpression,
                                                int page,
                                                int pageSize,
                                                int timezoneOffset) throws WrongFilterException {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("LIMIT", pageSize);
        params.addValue("OFFSET", (page - 1) * pageSize);

        Map<String, Long> parametersPlaceholders = new HashedMap<>();

        Map<String, Object> converterParams = new HashedMap<>();
        converterParams.put(DateConverter.TIMEZONE_OFFSET_PARAMETER, timezoneOffset);

        filterExpression = filterExpression.preProcessExpression(
                FilterRunParameters.class,
                params,
                parametersPlaceholders,
                converterParams);

        String query = wherePattern
                .matcher(this.filterPipelineRunsBaseQuery)
                .replaceFirst(filterExpression.toSQLStatement());
        return getNamedParameterJdbcTemplate().query(query, params, FilterRunParameters.getRowMapper());
    }

    public int countFilterPipelineRuns(FilterExpression filterExpression,
                                       int timezoneOffset) throws WrongFilterException {

        MapSqlParameterSource params = new MapSqlParameterSource();
        Map<String, Long> parametersPlaceholders = new HashedMap<>();
        Map<String, Object> converterParams = new HashedMap<>();
        converterParams.put(DateConverter.TIMEZONE_OFFSET_PARAMETER, timezoneOffset);
        filterExpression = filterExpression.preProcessExpression(
                FilterRunParameters.class,
                params,
                parametersPlaceholders,
                converterParams);

        String query = wherePattern
                .matcher(this.countFilteredPipelineRunsBaseQuery)
                .replaceFirst(filterExpression.toSQLStatement());
        return getNamedParameterJdbcTemplate().queryForObject(query, params, Integer.class);
    }

    @Required
    public void setFilterPipelineRunsBaseQuery(String filterPipelineRunsBaseQuery) {
        this.filterPipelineRunsBaseQuery = filterPipelineRunsBaseQuery;
    }

    @Required
    public void setCountFilteredPipelineRunsBaseQuery(String countFilteredPipelineRunsBaseQuery) {
        this.countFilteredPipelineRunsBaseQuery = countFilteredPipelineRunsBaseQuery;
    }

}
