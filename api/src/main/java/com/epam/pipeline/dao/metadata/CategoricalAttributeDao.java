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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CategoricalAttributeDao extends NamedParameterJdbcDaoSupport {

    private static final String LIST_PARAMETER = "list";

    @Autowired
    private MessageHelper messageHelper;
    private String insertAttributeValueQuery;
    private String loadAllAttributesValuesQuery;
    private String loadAllAttributesValuesWithoutLinksQuery;
    private String loadAttributesValuesQuery;
    private String deleteAttributeValuesQuery;
    private String deleteAttributeValueQuery;
    private String insertAttributeValueLinkQuery;

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
            .map(e -> new CategoricalAttribute(e.getKey(),
                                               e.getValue().stream()
                                                   .map(v -> new CategoricalAttributeValue(e.getKey(), v))
                                                   .collect(Collectors.toList())))
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
            .filter(attribute -> Objects.nonNull(attribute.getKey()))
            .flatMap(entry -> CollectionUtils.emptyIfNull(entry.getValues())
                .stream()
                .filter(value -> Objects.nonNull(value.getValue()))
                .map(value -> AttributeValueParameters.getValueParameters(entry.getKey(), value.getValue())))
            .toArray(MapSqlParameterSource[]::new);
        if (values.length == 0) {
            return false;
        }
        final boolean valuesChanges =
            rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueQuery, values));
        final Map<Pair<String, String>, Long> pairsIds = loadAll(false).stream()
            .flatMap(attribute -> CollectionUtils.emptyIfNull(attribute.getValues()).stream())
            .collect(Collectors.toMap(value -> Pair.of(value.getKey(), value.getValue()),
                                      CategoricalAttributeValue::getId));
        final MapSqlParameterSource[] links = dict.stream()
            .flatMap(entry -> CollectionUtils.emptyIfNull(entry.getValues())
                .stream())
            .flatMap(attributeValue -> CollectionUtils.emptyIfNull(attributeValue.getLinks())
                .stream()
                .map(link -> getLinkParameters(pairsIds, attributeValue, link)))
            .toArray(MapSqlParameterSource[]::new);
        final boolean linksChanges =
            links.length > 0
            && rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueLinkQuery, links));
        return valuesChanges || linksChanges;
    }

    public List<CategoricalAttribute> loadAll() {
        return loadAll(true);
    }

    public List<CategoricalAttribute> loadAll(final boolean loadWithLinks) {
        return getNamedParameterJdbcTemplate()
            .query(loadWithLinks
                   ? loadAllAttributesValuesQuery
                   : loadAllAttributesValuesWithoutLinksQuery,
                   AttributeValueParameters.getRowMapper(loadWithLinks))
            .stream()
            .collect(Collectors.groupingBy(CategoricalAttributeValue::getKey))
            .entrySet()
            .stream()
            .map(entry -> new CategoricalAttribute(entry.getKey(), mergeLinks(entry.getValue())))
            .collect(Collectors.toList());
    }

    public CategoricalAttribute loadAllValuesForKey(final String key) {
        final List<CategoricalAttributeValue> values = getNamedParameterJdbcTemplate()
            .query(loadAttributesValuesQuery, AttributeValueParameters.getValueParameters(key),
                   AttributeValueParameters.getRowMapper(true));
        return CollectionUtils.isEmpty(values)
               ? null
               : new CategoricalAttribute(key, mergeLinks(values));
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
                   .update(deleteAttributeValueQuery, AttributeValueParameters.getValueParameters(key, value)) > 0;
    }


    private MapSqlParameterSource getLinkParameters(final Map<Pair<String, String>, Long> pairsIds,
                                                    final CategoricalAttributeValue attributeValue,
                                                    final CategoricalAttributeValue link) {
        final Long childId = pairsIds.get(Pair.of(link.getKey(), link.getValue()));
        if (childId == null) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_CATEGORICAL_ATTRIBUTE_INVALID_LINK,
                attributeValue.getKey(), attributeValue.getValue(), link.getKey(), link.getValue()));
        }
        final Long parentId = pairsIds.get(Pair.of(attributeValue.getKey(), attributeValue.getValue()));
        return AttributeValueParameters.getLinkParameters(parentId, childId, link.getAutofill());
    }

    private boolean rowsChanged(final int[] changes) {
        return Arrays.stream(changes).anyMatch(num -> num == 1);
    }

    private List<CategoricalAttributeValue> mergeLinks(final List<CategoricalAttributeValue> values) {
        return values.stream()
            .collect(Collectors.groupingBy(CategoricalAttributeValue::getId))
            .entrySet()
            .stream()
            .map(entry -> {
                final List<CategoricalAttributeValue> valuesWithLink = entry.getValue();
                final CategoricalAttributeValue value = new CategoricalAttributeValue(entry.getKey(),
                                                                                      valuesWithLink.get(0).getKey(),
                                                                                      valuesWithLink.get(0).getValue());
                final List<CategoricalAttributeValue> allLinks = valuesWithLink.stream()
                    .map(CategoricalAttributeValue::getLinks)
                    .flatMap(Collection::stream).collect(Collectors.toList());
                value.setLinks(allLinks);
                return value;
            })
            .collect(Collectors.toList());
    }

    enum AttributeValueParameters {
        KEY,
        VALUE,
        ID,
        PARENT_ID,
        CHILD_ID,
        AUTOFILL,
        CHILD_KEY,
        CHILD_VALUE;

        private static MapSqlParameterSource getValueParameters(final String key) {
            return getValueParameters(key, null);
        }

        private static MapSqlParameterSource getValueParameters(final String key, final String value) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(KEY.name(), key);
            params.addValue(VALUE.name(), value);
            return params;
        }

        private static MapSqlParameterSource getLinkParameters(final Long parentId, final Long childId,
                                                               final Boolean autofill) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(PARENT_ID.name(), parentId);
            params.addValue(CHILD_ID.name(), childId);
            params.addValue(AUTOFILL.name(), autofill);
            return params;
        }

        private static RowMapper<CategoricalAttributeValue> getRowMapper(final boolean mappingWithLinks) {
            return (rs, rowNum) -> {
                final CategoricalAttributeValue value = new CategoricalAttributeValue(rs.getLong(ID.name()),
                                                                                      rs.getString(KEY.name()),
                                                                                      rs.getString(VALUE.name()));
                if (mappingWithLinks) {
                    final String childKey = rs.getString(CHILD_KEY.name());
                    final List<CategoricalAttributeValue> links = new ArrayList<>();
                    if (childKey != null) {
                        links.add(new CategoricalAttributeValue(rs.getLong(CHILD_ID.name()),
                                                                rs.getString(CHILD_KEY.name()),
                                                                rs.getString(CHILD_VALUE.name()),
                                                                rs.getBoolean(AUTOFILL.name())));
                    }
                    value.setLinks(links);
                }
                return value;
            };
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
    public void setLoadAllAttributesValuesWithoutLinksQuery(String loadAllAttributesValuesWithoutLinksQuery) {
        this.loadAllAttributesValuesWithoutLinksQuery = loadAllAttributesValuesWithoutLinksQuery;
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

    @Required
    public void setInsertAttributeValueLinkQuery(String insertAttributeValueLinkQuery) {
        this.insertAttributeValueLinkQuery = insertAttributeValueLinkQuery;
    }
}
