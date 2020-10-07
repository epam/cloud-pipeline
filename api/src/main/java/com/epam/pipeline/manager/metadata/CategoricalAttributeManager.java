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
        final boolean valuesChanged = categoricalAttributesDao.insertAttributesValues(dict);
        final Map<String, Set<String>> receivedValues = dict.stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey,
                attribute -> attribute
                    .getValues()
                    .stream()
                    .map(CategoricalAttributeValue::getValue)
                    .collect(Collectors.toSet())));
        final List<CategoricalAttributeValue> valuesToRemove = loadAll().stream()
            .flatMap(entry -> CollectionUtils.emptyIfNull(entry.getValues())
                .stream()
                .peek(attributeValue -> attributeValue.setKey(entry.getKey())))
            .filter(value -> receivedValues.containsKey(value.getKey()))
            .filter(value -> !receivedValues.get(value.getKey()).contains(value.getValue()))
            .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(valuesToRemove)) {
            categoricalAttributesDao.deleteAttributeValuePairs(valuesToRemove);
        }
        return categoricalAttributesDao.insertValuesLinks(dict) || valuesChanged;
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
}
