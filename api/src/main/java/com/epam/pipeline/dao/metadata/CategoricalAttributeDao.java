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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.BaseEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CategoricalAttributeDao extends NamedParameterJdbcDaoSupport {

    private static final String LIST_PARAMETER = "list";

    @Autowired
    private MessageHelper messageHelper;
    @Autowired
    private DaoHelper daoHelper;

    private String createAttributeQuery;
    private String insertAttributeValueQuery;
    private String loadAllAttributesValuesQuery;
    private String loadAllAttributesValuesWithoutLinksQuery;
    private String loadAttributeValuesQuery;
    private String loadAttributesValuesQuery;
    private String deleteAttributeValuesQuery;
    private String deleteAttributeValueQuery;
    private String insertAttributeValueLinkQuery;
    private String deleteAttributeValueLinkQuery;
    private String updateAttributeQuery;
    private String loadAttributeValuesByAttributeIdQuery;
    private String categoricalAttributeSequence;

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
    public boolean insertAttributesValues(final List<CategoricalAttribute> dict) {
        final MapSqlParameterSource[] values = dict.stream()
            .filter(attribute -> Objects.nonNull(attribute.getName()))
            .flatMap(entry -> CollectionUtils.emptyIfNull(entry.getValues())
                .stream()
                .filter(value -> Objects.nonNull(value.getValue()))
                .map(value -> AttributeValueParameters.getValueParameters(entry.getName(), value.getValue(),
                                                                          entry.getOwner())))
            .toArray(MapSqlParameterSource[]::new);
        return values.length != 0
               && rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueQuery, values));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean createAttribute(final CategoricalAttribute attribute) {
        attribute.setId(daoHelper.createId(categoricalAttributeSequence));
        return getNamedParameterJdbcTemplate()
                   .update(createAttributeQuery, AttributeValueParameters.getAttributeParameters(attribute)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertValuesLinks(final List<CategoricalAttribute> dict) {
        final Map<Pair<String, String>, Long> pairsIds = loadAll(false).stream()
            .flatMap(attribute -> CollectionUtils.emptyIfNull(attribute.getValues()).stream())
            .collect(Collectors.toMap(value -> Pair.of(value.getKey(), value.getValue()),
                                      CategoricalAttributeValue::getId));
        final MapSqlParameterSource[] links = dict.stream()
            .flatMap(entry -> CollectionUtils.emptyIfNull(entry.getValues())
                .stream()
                .peek(attributeValue -> attributeValue.setKey(entry.getName())))
            .flatMap(attributeValue -> CollectionUtils.emptyIfNull(attributeValue.getLinks())
                .stream()
                .map(link -> getLinkParameters(pairsIds, attributeValue, link)))
            .toArray(MapSqlParameterSource[]::new);
        return links.length > 0
               && rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(insertAttributeValueLinkQuery, links));
    }

    public List<CategoricalAttribute> loadAll() {
        return loadAll(true);
    }

    public List<CategoricalAttribute> loadAll(final boolean loadWithLinks) {
        final List<CategoricalAttribute> allValues = getNamedParameterJdbcTemplate()
            .query(loadWithLinks
                   ? loadAllAttributesValuesQuery
                   : loadAllAttributesValuesWithoutLinksQuery,
                   AttributeValueParameters.getRowMapper(loadWithLinks));
        return mergeAttributes(allValues);
    }

    public CategoricalAttribute loadAllValuesForKey(final String key) {
        final List<CategoricalAttribute> values = getNamedParameterJdbcTemplate()
            .query(loadAttributeValuesQuery, AttributeValueParameters.getValueParameters(key),
                   AttributeValueParameters.getRowMapper(true));
        return mergeAttributes(values).stream().findAny().orElse(null);
    }

    public List<CategoricalAttribute> loadAllValuesForKeys(final Collection<String> keys) {
        final List<CategoricalAttribute> values = getNamedParameterJdbcTemplate()
            .query(loadAttributesValuesQuery, new MapSqlParameterSource(LIST_PARAMETER, keys),
                   AttributeValueParameters.getRowMapper(true));
        return mergeAttributes(values);
    }

    public CategoricalAttribute loadAttributeById(final Long attributeId) {
        final List<CategoricalAttribute> values = getNamedParameterJdbcTemplate()
            .query(loadAttributeValuesByAttributeIdQuery,
                   new MapSqlParameterSource(AttributeValueParameters.ATTRIBUTE_ID.name(), attributeId),
                   AttributeValueParameters.getRowMapper(true));
        return mergeAttributes(values).stream().findAny().orElse(null);
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
    public boolean deleteSpecificAttributeValues(final List<CategoricalAttribute> attributes) {
        final MapSqlParameterSource[] valuesToRemove =
            attributes.stream().flatMap(attribute -> CollectionUtils.emptyIfNull(attribute.getValues())
                .stream()
                .peek(attributeValue -> attributeValue.setKey(attribute.getName())))
                .map(p -> AttributeValueParameters.getValueParameters(p.getKey(), p.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        return rowsChanged(getNamedParameterJdbcTemplate().batchUpdate(deleteAttributeValueQuery, valuesToRemove));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteAttributeValue(final String key, final String value) {
        return getNamedParameterJdbcTemplate()
                   .update(deleteAttributeValueQuery, AttributeValueParameters.getValueParameters(key, value)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deleteLinks(final List<Pair<Long, Long>> deletedLinks) {
        final MapSqlParameterSource[] valuesToRemove =
                deletedLinks
                        .stream()
                        .map(pair -> {
                            final MapSqlParameterSource params = new MapSqlParameterSource();
                            params.addValue(AttributeValueParameters.PARENT_ID.name(), pair.getLeft());
                            params.addValue(AttributeValueParameters.CHILD_ID.name(), pair.getRight());
                            return params;
                        })
                        .toArray(MapSqlParameterSource[]::new);
        return rowsChanged(getNamedParameterJdbcTemplate()
                .batchUpdate(deleteAttributeValueLinkQuery, valuesToRemove));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateAttribute(final CategoricalAttribute attribute) {
        return getNamedParameterJdbcTemplate()
                   .update(updateAttributeQuery, AttributeValueParameters.getAttributeParameters(attribute)) > 0;
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
                final CategoricalAttributeValue firstValue = valuesWithLink.get(0);
                final CategoricalAttributeValue value = new CategoricalAttributeValue(entry.getKey(),
                                                                                      firstValue.getAttributeId(),
                                                                                      firstValue.getKey(),
                                                                                      firstValue.getValue());
                final List<CategoricalAttributeValue> allLinks = valuesWithLink.stream()
                    .map(CategoricalAttributeValue::getLinks)
                    .flatMap(Collection::stream).collect(Collectors.toList());
                value.setLinks(allLinks);
                return value;
            })
            .collect(Collectors.toList());
    }

    private List<CategoricalAttribute> mergeAttributes(final List<CategoricalAttribute> values) {
        return new ArrayList<>(
            values.stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity(), this::mergeAttributes))
                .values());
    }

    private CategoricalAttribute mergeAttributes(final CategoricalAttribute attribute1,
                                                 final CategoricalAttribute attribute2) {
        final Set<CategoricalAttributeValue> mergedValues = new HashSet<>(attribute1.getValues());
        mergedValues.addAll(attribute2.getValues());
        attribute1.setValues(mergeLinks(new ArrayList<>(mergedValues)));
        return attribute1;
    }

    enum AttributeValueParameters {
        ATTRIBUTE_ID,
        NAME,
        VALUE,
        ID,
        CREATED_DATE,
        OWNER,
        PARENT_ID,
        CHILD_ID,
        AUTOFILL,
        CHILD_NAME,
        CHILD_VALUE,
        CHILD_ATTRIBUTE_ID;

        private static MapSqlParameterSource getAttributeParameters(final CategoricalAttribute attribute) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ATTRIBUTE_ID.name(), attribute.getId());
            params.addValue(NAME.name(), attribute.getName());
            params.addValue(OWNER.name(), attribute.getOwner());
            return params;
        }

        private static MapSqlParameterSource getValueParameters(final String key) {
            return getValueParameters(key, null);
        }

        private static MapSqlParameterSource getValueParameters(final String key, final String value) {
            return getValueParameters(key, value, null);
        }

        private static MapSqlParameterSource getValueParameters(final String key, final String value,
                                                                final String owner) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(NAME.name(), key);
            params.addValue(VALUE.name(), value);
            params.addValue(OWNER.name(), owner);
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

        private static RowMapper<CategoricalAttribute> getRowMapper(final boolean mappingWithLinks) {
            return (rs, rowNum) -> {
                final String attributeName = rs.getString(NAME.name());
                final long attributeId = rs.getLong(ATTRIBUTE_ID.name());
                final CategoricalAttributeValue value = new CategoricalAttributeValue(rs.getLong(ID.name()),
                                                                                      attributeId,
                                                                                      attributeName,
                                                                                      rs.getString(VALUE.name()));
                if (mappingWithLinks) {
                    final String childKey = rs.getString(CHILD_NAME.name());
                    final List<CategoricalAttributeValue> links = new ArrayList<>();
                    if (childKey != null) {
                        final CategoricalAttributeValue link =
                            new CategoricalAttributeValue(rs.getLong(CHILD_ID.name()),
                                                          rs.getLong(CHILD_ATTRIBUTE_ID.name()),
                                                          rs.getString(CHILD_NAME.name()),
                                                          rs.getString(CHILD_VALUE.name()),
                                                          rs.getBoolean(AUTOFILL.name()));
                        links.add(link);
                    }
                    value.setLinks(links);
                }
                final CategoricalAttribute attribute =
                    new CategoricalAttribute(attributeName, Collections.singletonList(value));
                attribute.setId(attributeId);
                attribute.setOwner(rs.getString(OWNER.name()));
                attribute.setCreatedDate(rs.getDate(CREATED_DATE.name()));
                return attribute;
            };
        }
    }

    @Required
    public void setCreateAttributeQuery(String createAttributeQuery) {
        this.createAttributeQuery = createAttributeQuery;
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
    public void setLoadAttributeValuesQuery(String loadAttributeValuesQuery) {
        this.loadAttributeValuesQuery = loadAttributeValuesQuery;
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

    @Required
    public void setDeleteAttributeValueLinkQuery(String deleteAttributeValueLinkQuery) {
        this.deleteAttributeValueLinkQuery = deleteAttributeValueLinkQuery;
    }

    @Required
    public void setUpdateAttributeQuery(String updateAttributeQuery) {
        this.updateAttributeQuery = updateAttributeQuery;
    }

    @Required
    public void setLoadAttributeValuesByAttributeIdQuery(String loadAttributeValuesByAttributeIdQuery) {
        this.loadAttributeValuesByAttributeIdQuery = loadAttributeValuesByAttributeIdQuery;
    }

    @Required
    public void setCategoricalAttributeSequence(String categoricalAttributeSequence) {
        this.categoricalAttributeSequence = categoricalAttributeSequence;
    }
}
