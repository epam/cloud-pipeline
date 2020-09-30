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

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    public static List<CategoricalAttribute> convertPairsToAttributesList(final List<Pair<String, String>> pairs) {
        return pairs.stream()
            .collect(Collectors.groupingBy(Pair::getKey,
                                           Collector.of(ArrayList<String>::new,
                                               (list, pair) -> list.add(pair.getValue()),
                                               (left, right) -> {
                                                   left.addAll(right);
                                                   return left;
                                               },
                                               Function.identity())))
            .entrySet()
            .stream()
            .map(e -> new CategoricalAttribute(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateCategoricalAttributes(final List<CategoricalAttribute> dict) {
        final List<String> dictionaries = dict.stream()
            .map(CategoricalAttribute::getKey)
            .collect(Collectors.toList());
        this.deleteAttributeValues(dictionaries);
        return this.insertAttributesValues(dict);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertAttributesValues(final List<CategoricalAttribute> dict) {
        final MapSqlParameterSource[] values = dict.stream()
            .flatMap(entry -> entry.getValues()
                .stream()
                .map(value -> AttributeValueParameters.getParameters(entry.getKey(), value)))
            .toArray(MapSqlParameterSource[]::new);
        return rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueQuery, values));
    }

    public List<CategoricalAttribute> loadAll() {
        final List<Pair<String, String>> allAttributeValues = getNamedParameterJdbcTemplate()
            .query(loadAllAttributesValuesQuery, AttributeValueParameters.getRowMapper());
        return convertPairsToAttributesList(allAttributeValues);
    }

    public CategoricalAttribute loadAllValuesForKey(final String key) {
        final List<String> values = getNamedParameterJdbcTemplate()
            .query(loadAttributesValuesQuery, AttributeValueParameters.getParameters(key),
                   AttributeValueParameters.getRowMapper())
            .stream()
            .map(Pair::getValue)
            .collect(Collectors.toList());
        return CollectionUtils.isEmpty(values)
               ? null
               : new CategoricalAttribute(key, values);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValues(final String key) {
        return deleteAttributeValues(Collections.singletonList(key));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValues(final List<String> keys) {
        return !CollectionUtils.isEmpty(keys)
               && getNamedParameterJdbcTemplate()
                      .update(deleteAttributeValuesQuery, new MapSqlParameterSource(LIST_PARAMETER, keys)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValue(final String key, final String value) {
        return getNamedParameterJdbcTemplate()
                   .update(deleteAttributeValueQuery, AttributeValueParameters.getParameters(key, value)) > 0;
    }

    private boolean rowsChanged(final int[] changes) {
        return Arrays.stream(changes).anyMatch(num -> num == 1);
    }

    enum AttributeValueParameters {
        KEY,
        VALUE;

        private static MapSqlParameterSource getParameters(final String key) {
            return getParameters(key, null);
        }

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
