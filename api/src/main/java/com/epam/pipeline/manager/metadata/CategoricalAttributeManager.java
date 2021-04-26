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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.metadata.CategoricalAttributeDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@AclSync
public class CategoricalAttributeManager implements SecuredEntityManager {

    private final CategoricalAttributeDao categoricalAttributesDao;
    private final MessageHelper messageHelper;
    private final AuthManager securityManager;

    @Override
    public AclClass getSupportedClass() {
        return AclClass.CATEGORICAL_ATTRIBUTE;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CategoricalAttribute create(final CategoricalAttribute attribute) {
        final String key = attribute.getName();
        Assert.isNull(categoricalAttributesDao.loadAllValuesForKey(key),
                      messageHelper.getMessage(MessageConstants.ERROR_CATEGORICAL_ATTRIBUTE_EXISTS_ALREADY, key));
        final CategoricalAttribute attributeToCreate = setOwner(attribute);
        categoricalAttributesDao.createAttribute(attributeToCreate);
        updateValues(Collections.singletonList(attribute));
        return attributeToCreate;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CategoricalAttribute update(final String attributeKey, final CategoricalAttribute attribute) {
        final CategoricalAttribute existingAttribute = loadByNameOrId(attributeKey);
        if (attribute.getOwner() == null) {
            attribute.setOwner(existingAttribute.getOwner());
        }
        if (!(existingAttribute.getOwner().equals(attribute.getOwner())
              && existingAttribute.getName().equals(attribute.getName()))) {
            attribute.setId(existingAttribute.getId());
            categoricalAttributesDao.updateAttribute(attribute);
        }
        updateValues(Collections.singletonList(attribute));
        return attribute;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean updateValues(final List<CategoricalAttribute> dict) {
        final List<CategoricalAttribute> currentValues =
            categoricalAttributesDao.loadAllValuesForKeys(dict.stream()
                                                              .map(CategoricalAttribute::getName)
                                                              .collect(Collectors.toList()));
        final List<CategoricalAttribute> attributeWithValuesToRemove =
            keepAttributesWithValuesToRemove(dict, currentValues);
        final boolean valuesRemoved = CollectionUtils.isNotEmpty(attributeWithValuesToRemove)
               && categoricalAttributesDao.deleteSpecificAttributeValues(attributeWithValuesToRemove);

        final List<Pair<Long, Long>> deletedLinks = getDeletedLinks(dict, currentValues);
        final boolean linksRemoved = CollectionUtils.isNotEmpty(deletedLinks) &&
                categoricalAttributesDao.deleteLinks(deletedLinks);

        final List<CategoricalAttribute> attributesWithValuesToInsert =
            keepAttributesWithValuesToInsert(dict, currentValues);
        final boolean valuesInserted =  CollectionUtils.isNotEmpty(attributesWithValuesToInsert)
               && categoricalAttributesDao.insertAttributesValues(setOwner(attributesWithValuesToInsert));

        return categoricalAttributesDao.insertValuesLinks(dict) || valuesInserted || valuesRemoved || linksRemoved;
    }

    @Override
    public CategoricalAttribute load(final Long id) {
        final CategoricalAttribute attribute = categoricalAttributesDao.loadAttributeById(id);
        Assert.notNull(attribute,
                       messageHelper.getMessage(MessageConstants.ERROR_CATEGORICAL_ATTRIBUTE_ID_DOESNT_EXIST, id));
        return attribute;
    }

    @Override
    public CategoricalAttribute loadWithParents(final Long id) {
        return load(id);
    }

    public List<CategoricalAttribute> loadAll() {
        return categoricalAttributesDao.loadAll();
    }

    @Override
    public CategoricalAttribute loadByNameOrId(final String key) {
        final CategoricalAttribute categoricalAttribute = categoricalAttributesDao.loadAllValuesForKey(key);
        Assert.notNull(categoricalAttribute,
                       messageHelper.getMessage(MessageConstants.ERROR_CATEGORICAL_ATTRIBUTE_KEY_DOESNT_EXIST, key));
        return categoricalAttribute;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean delete(final String key) {
        return categoricalAttributesDao.deleteAttributeValues(key);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean deleteAttributeValue(final String key, final String value) {
        return categoricalAttributesDao.deleteAttributeValue(key, value);
    }

    @Override
    public CategoricalAttribute changeOwner(final Long id, final String owner) {
        final CategoricalAttribute attribute = load(id);
        attribute.setOwner(owner);
        categoricalAttributesDao.updateAttribute(attribute);
        return attribute;
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(final Integer page, final Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    private List<CategoricalAttribute> setOwner(final List<CategoricalAttribute> attributes) {
        return attributes.stream().map(this::setOwner).collect(Collectors.toList());
    }

    private CategoricalAttribute setOwner(final CategoricalAttribute attribute) {
        if (attribute.getOwner() == null) {
            attribute.setOwner(securityManager.getAuthorizedUser());
        }
        return attribute;
    }

    private List<CategoricalAttribute> keepAttributesWithValuesToRemove(final List<CategoricalAttribute> receivedState,
                                                                        final List<CategoricalAttribute> currentState) {
        final Map<String, Set<String>> receivedValues = collectAttributesToMap(receivedState);
        return currentState.stream()
            .filter(value -> receivedValues.containsKey(value.getName()))
            .map(attribute -> {
                final List<CategoricalAttributeValue> newValues = CollectionUtils.emptyIfNull(attribute.getValues())
                    .stream()
                    .filter(value -> !receivedValues.get(value.getKey()).contains(value.getValue()))
                    .collect(Collectors.toList());
                return new CategoricalAttribute(attribute.getName(), newValues);
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
                    .filter(value -> !currentValues.containsKey(attribute.getName())
                                        || !currentValues.get(attribute.getName()).contains(value.getValue()))
                    .collect(Collectors.toList());
                return new CategoricalAttribute(attribute.getName(), newValues);
            })
            .filter(attribute -> CollectionUtils.isNotEmpty(attribute.getValues()))
            .collect(Collectors.toList());
    }

    private Map<String, Set<String>> collectAttributesToMap(final List<CategoricalAttribute> attributes) {
        return attributes.stream()
            .collect(Collectors.toMap(CategoricalAttribute::getName,
                attribute -> attribute
                    .getValues()
                    .stream()
                    .map(CategoricalAttributeValue::getValue)
                    .collect(Collectors.toSet())));
    }

    private List<Pair<Long, Long>> getDeletedLinks(final List<CategoricalAttribute> receivedState,
                                                   final List<CategoricalAttribute> currentState) {
        final Map<String, List<CategoricalAttributeValue>> newStateMap = receivedState.stream()
                .collect(Collectors.toMap(CategoricalAttribute::getName,
                        CategoricalAttribute::getValues, (a1, a2) -> a2));
        return ListUtils.emptyIfNull(currentState)
                .stream()
                .map(currentAttribute -> getDeletedLinksForAttribute(newStateMap, currentAttribute))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<ImmutablePair<Long, Long>> getDeletedLinksForAttribute(
            final Map<String, List<CategoricalAttributeValue>> newStateMap,
            final CategoricalAttribute currentAttribute) {
        final Map<String, CategoricalAttributeValue> newValues = ListUtils.emptyIfNull(
                newStateMap.get(currentAttribute.getName()))
                .stream()
                .collect(Collectors.toMap(
                        CategoricalAttributeValue::getValue, Function.identity(), (v1, v2) -> v2));
        return ListUtils.emptyIfNull(currentAttribute.getValues())
                .stream()
                .map(currentValue -> getDeletedLinksForValue(newValues, currentValue))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<ImmutablePair<Long, Long>> getDeletedLinksForValue(
            final Map<String, CategoricalAttributeValue> newValues,
            final CategoricalAttributeValue currentValue) {
        final List<CategoricalAttributeValue> newLinks = Optional.ofNullable(newValues.get(currentValue.getValue()))
                .map(CategoricalAttributeValue::getLinks)
                .orElse(Collections.emptyList());
        return ListUtils.emptyIfNull(currentValue.getLinks())
                .stream()
                .filter(currentLink -> ListUtils.emptyIfNull(newLinks).stream()
                        .noneMatch(newLink -> isSameLink(currentLink, newLink)))
                .map(deleteLink -> new ImmutablePair<>(currentValue.getId(), deleteLink.getId()))
                .collect(Collectors.toList());
    }

    private boolean isSameLink(final CategoricalAttributeValue currentLink,
                               final CategoricalAttributeValue newLink) {
        return newLink.getKey().equals(currentLink.getKey()) &&
                newLink.getValue().equals(currentLink.getValue());
    }
}
