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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.metadata.CategoricalAttributeDao;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoricalAttributeManager {

    private final CategoricalAttributeDao categoricalAttributesDao;
    private final MetadataManager metadataManager;
    private final MessageHelper messageHelper;


    @Transactional(propagation = Propagation.REQUIRED)
    public boolean updateCategoricalAttributes(final List<CategoricalAttribute> dict) {
        final List<CategoricalAttribute> currentValues =
            categoricalAttributesDao.loadAllValuesForKeys(dict.stream()
                                                              .map(CategoricalAttribute::getKey)
                                                              .collect(Collectors.toList()));
        final List<CategoricalAttribute> attributeWithValuesToRemove =
            keepAttributesWithValuesToRemove(dict, currentValues);
        final boolean valuesRemoved = CollectionUtils.isNotEmpty(attributeWithValuesToRemove)
               && categoricalAttributesDao.deleteSpecificAttributeValues(attributeWithValuesToRemove);
        final List<CategoricalAttribute> attributesWithValuesToInsert =
            keepAttributesWithValuesToInsert(dict, currentValues);
        final boolean valuesInserted =  CollectionUtils.isNotEmpty(attributesWithValuesToInsert)
               && categoricalAttributesDao.insertAttributesValues(attributesWithValuesToInsert);
        return categoricalAttributesDao.insertValuesLinks(dict) || valuesInserted || valuesRemoved;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean insertAttributesValues(final List<CategoricalAttribute> dict) {
        return categoricalAttributesDao.insertAttributesValues(dict);
    }

    public List<CategoricalAttribute> loadAll() {
        return categoricalAttributesDao.loadAll();
    }

    public CategoricalAttribute loadAllValuesForKey(final String key) {
        final CategoricalAttribute categoricalAttribute = categoricalAttributesDao.loadAllValuesForKey(key);
        Assert.notNull(categoricalAttribute,
                       messageHelper.getMessage(MessageConstants.ERROR_CATEGORICAL_ATTRIBUTE_DOESNT_EXIST, key));
        return categoricalAttribute;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteAttributeValues(final String key) {
        return categoricalAttributesDao.deleteAttributeValues(key);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteAttributeValue(final String key, final String value) {
        return categoricalAttributesDao.deleteAttributeValue(key, value);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void syncWithMetadata() {
        final List<CategoricalAttribute> fullMetadataDict = metadataManager.buildFullMetadataDict();
        insertAttributesValues(fullMetadataDict);
    }

    private List<CategoricalAttribute> keepAttributesWithValuesToRemove(final List<CategoricalAttribute> receivedState,
                                                                        final List<CategoricalAttribute> currentState) {
        final Map<String, Set<String>> receivedValues = collectAttributesToMap(receivedState);
        return currentState.stream()
            .filter(value -> receivedValues.containsKey(value.getKey()))
            .map(attribute -> {
                final List<CategoricalAttributeValue> newValues = CollectionUtils.emptyIfNull(attribute.getValues())
                    .stream()
                    .filter(value -> !receivedValues.get(value.getKey()).contains(value.getValue()))
                    .collect(Collectors.toList());
                return new CategoricalAttribute(attribute.getKey(), newValues);
            })
            .filter(attribute -> CollectionUtils.isNotEmpty(attribute.getValues()))
            .collect(Collectors.toList());
    }

    private List<CategoricalAttribute> keepAttributesWithValuesToInsert(final List<CategoricalAttribute> receivedState,
                                                                        final List<CategoricalAttribute> currentState) {
        final Map<String, Set<String>> currentValues = collectAttributesToMap(currentState);
        return receivedState.stream()
            .map(attribute -> {
                final List<CategoricalAttributeValue> newValues = CollectionUtils.emptyIfNull(attribute.getValues())
                    .stream()
                    .filter(value -> !currentValues.containsKey(attribute.getKey())
                                        || !currentValues.get(attribute.getKey()).contains(value.getValue()))
                    .collect(Collectors.toList());
                return new CategoricalAttribute(attribute.getKey(), newValues);
            })
            .filter(attribute -> CollectionUtils.isNotEmpty(attribute.getValues()))
            .collect(Collectors.toList());
    }

    private Map<String, Set<String>> collectAttributesToMap(final List<CategoricalAttribute> attributes) {
        return attributes.stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey,
                attribute -> attribute
                    .getValues()
                    .stream()
                    .map(CategoricalAttributeValue::getValue)
                    .collect(Collectors.toSet())));
    }
}
