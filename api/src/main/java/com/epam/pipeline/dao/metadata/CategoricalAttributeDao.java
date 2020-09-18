/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CategoricalAttributeDao extends NamedParameterJdbcDaoSupport {

    private static final String LIST_PARAMETER = "list";

    private String insertAttributeValueQuery;
    private String loadAllAttributesValuesQuery;
    private String loadAttributesValuesQuery;
    private String deleteAttributeValuesQuery;
    private String deleteAttributeValueQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertAttributesValues(final Map<String, List<String>> dict) {
        final MapSqlParameterSource[] values = dict.entrySet().stream()
            .flatMap(entry -> entry.getValue()
                .stream()
                .map(value -> AttributeValueParameters.getParameters(entry.getKey(), value)))
            .toArray(MapSqlParameterSource[]::new);
        return rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueQuery, values));
    }

    public Map<String, List<String>> loadAll() {
        final List<Pair<String, String>> allAttributeValues = getNamedParameterJdbcTemplate()
            .query(loadAllAttributesValuesQuery, AttributeValueParameters.getRowMapper());
        return listOfPairsToMap(allAttributeValues);
    }

    public Map<String, List<String>> loadAllValuesForKeys(final List<String> keys) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(LIST_PARAMETER, keys);
        final List<Pair<String, String>> requestedAttributesValues = getNamedParameterJdbcTemplate()
            .query(loadAttributesValuesQuery, params, AttributeValueParameters.getRowMapper());
        return listOfPairsToMap(requestedAttributesValues);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValuesQuery(final String key) {
        return getNamedParameterJdbcTemplate()
                   .update(deleteAttributeValuesQuery, AttributeValueParameters.getParameters(key, null)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValueQuery(final String key, final String value) {
        return getNamedParameterJdbcTemplate()
                   .update(deleteAttributeValueQuery, AttributeValueParameters.getParameters(key, value)) > 0;
    }

    private Map<String, List<String>> listOfPairsToMap(final List<Pair<String, String>> pairs) {
        return pairs.stream()
            .collect(Collectors.groupingBy(Pair::getKey,
                                           Collector.of(ArrayList::new,
                                                        (list, pair) -> list.add(pair.getValue()),
                                                        (left, right) -> { left.addAll(right); return left; },
                                                        Function.identity())));
    }

    private boolean rowsChanged(final int[] changes) {
        return Arrays.stream(changes).anyMatch(num -> num == 1);
    }

    enum AttributeValueParameters {
        KEY,
        VALUE;

        private static MapSqlParameterSource getParameters(final String key, final String value) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(KEY.name(), key);
            params.addValue(VALUE.name(), value);
            return params;
        }

        private static RowMapper<Pair<String, String>> getRowMapper() {
            return (rs, rowNum) -> Pair.of(rs.getString(KEY.name()), rs.getString(VALUE.name()));
        }
    }

    @Required
    public void setInsertAttributeValueQuery(String insertAttributeValueQuery) {
        this.insertAttributeValueQuery = insertAttributeValueQuery;
    }

    @Required
    public void setLoadAllAttributesValuesQuery(String loadAllAttributesValuesQuery) {
        this.loadAllAttributesValuesQuery = loadAllAttributesValuesQuery;
    }

    @Required
    public void setLoadAttributesValuesQuery(String loadAttributesValuesQuery) {
        this.loadAttributesValuesQuery = loadAttributesValuesQuery;
    }

    @Required
    public void setDeleteAttributeValuesQuery(String deleteAttributeValuesQuery) {
        this.deleteAttributeValuesQuery = deleteAttributeValuesQuery;
    }

    @Required
    public void setDeleteAttributeValueQuery(String deleteAttributeValueQuery) {
        this.deleteAttributeValueQuery = deleteAttributeValueQuery;
    }
}
