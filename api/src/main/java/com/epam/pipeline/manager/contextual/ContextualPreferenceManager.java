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

package com.epam.pipeline.manager.contextual;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class ContextualPreferenceManager {

    private final ContextualPreferenceDao contextualPreferenceDao;
    private final ContextualPreferenceHandler contextualPreferenceHandler;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    /**
     * Loads all independent contextual preferences.
     *
     * Independent means that preferences from contextual preference table will be loaded.
     */
    public List<ContextualPreference> loadAll() {
        return contextualPreferenceDao.loadAll();
    }

    /**
     * Loads independent contextual preference with the given parameters.
     *
     * Independent means that preferences from contextual preference table will be loaded.
     *
     * @throws IllegalArgumentException if there is no preference with such parameters.
     */
    public ContextualPreference load(final String name, final ContextualPreferenceExternalResource resource) {
        validateName(name);
        validateResource(resource);
        return contextualPreferenceDao.load(name, resource)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NOT_FOUND, name, resource)));
    }

    private void validateName(final String name) {
        Assert.notNull(name, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NAME_MISSING));
    }

    private void validateResource(final ContextualPreferenceExternalResource resource) {
        Assert.notNull(resource, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_MISSING));
        Assert.notNull(resource.getLevel(), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_MISSING));
        Assert.notNull(resource.getResourceId(), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_ID_MISSING));
    }

    /**
     * Searches for a contextual preference with the given parameters
     *
     * Returns a first found preference from the list of preferences.
     *
     * Methods takes into account the context preference was searched in. It includes
     * user, its role, requested resource, etc.
     *
     * @param preferences List of preference names.
     * @param resource Optional external resource preferences should be found for.
     * @throws IllegalArgumentException if preference with such parameters wasn't found.
     */
    public ContextualPreference search(final List<String> preferences,
                                       final ContextualPreferenceExternalResource resource) {
        if (resource == null) {
            return search(preferences);
        }
        validateNames(preferences);
        validateResource(resource);
        Assert.isTrue(resource.getLevel() == ContextualPreferenceLevel.TOOL, messageHelper.getMessage(
                MessageConstants.ERROR_SEARCH_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_INVALID));
        return contextualPreferenceHandler.search(preferences, resources(resource))
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NOT_FOUND, preferences, resource)));
    }

    /**
     * Searches for a contextual preference with the given parameters
     *
     * Returns a first found preference from the list of preferences.
     *
     * Methods takes into account the context preference was searched in. It includes
     * user, its role, etc.
     *
     * @param preferences List of preference names.
     * @throws IllegalArgumentException if preference with such parameters wasn't found.
     */
    public ContextualPreference search(final List<String> preferences) {
        validateNames(preferences);
        return contextualPreferenceHandler.search(preferences, resources())
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NOT_FOUND, preferences, "no resource")));
    }

    private void validateNames(final List<String> names) {
        Assert.notNull(names, messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NAMES_MISSING));
        Assert.isTrue(!names.isEmpty(), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_NAMES_EMPTY));
    }

    private List<ContextualPreferenceExternalResource> resources() {
        return resources(null);
    }

    private List<ContextualPreferenceExternalResource> resources(final ContextualPreferenceExternalResource resource) {
        final Optional<PipelineUser> currentUser = retrieveCurrentUser();
        final List<ContextualPreferenceExternalResource> allResources = new ArrayList<>();
        currentUser.map(this::userResource).ifPresent(allResources::add);
        currentUser.map(PipelineUser::getRoles).map(this::rolesResources).ifPresent(allResources::addAll);
        if (resource != null) {
            allResources.add(resource);
        }
        return allResources;
    }

    private Optional<PipelineUser> retrieveCurrentUser() {
        final Optional<PipelineUser> authorizedUser = Optional.ofNullable(authManager.getCurrentUser());
        final Optional<PipelineUser> pipelineUserById = authorizedUser.map(PipelineUser::getId)
                .map(userManager::loadUserById);
        final Optional<PipelineUser> pipelineUserByUserName = authorizedUser.map(PipelineUser::getUserName)
                .map(userManager::loadUserByName);
        return pipelineUserById.isPresent()
                ? pipelineUserById
                : pipelineUserByUserName;
    }

    private ContextualPreferenceExternalResource userResource(final PipelineUser currentUser) {
        return new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, currentUser.getId().toString());
    }

    private List<ContextualPreferenceExternalResource> rolesResources(final List<Role> roles) {
        if (roles != null) {
            return roles.stream()
                    .map(role -> new ContextualPreferenceExternalResource(ContextualPreferenceLevel.ROLE,
                            role.getId().toString()))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Inserts or updates the given preference.
     *
     * @throws IllegalArgumentException if preference can't be inserted or updated.
     */
    @Transactional
    public ContextualPreference upsert(final ContextualPreferenceVO preferenceVO) {
        validatePreferenceFields(preferenceVO);
        validatePreferenceTypeAccordingToPreferencesWithTheSameName(preferenceVO);
        Assert.isTrue(preferenceVO.getResource().getLevel() != ContextualPreferenceLevel.SYSTEM,
                messageHelper.getMessage(
                        MessageConstants.ERROR_SAVE_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_INVALID));
        final ContextualPreference preference = preferenceFromVO(preferenceVO);
        if (contextualPreferenceHandler.isValid(preference)) {
            return contextualPreferenceDao.upsert(preference);
        } else {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_NOT_FOUND,
                    preference.getResource()));
        }
    }

    private void validatePreferenceTypeAccordingToPreferencesWithTheSameName(
            final ContextualPreferenceVO preferenceVO) {
        retrieveExpectedType(preferenceVO).ifPresent(type ->
                Assert.isTrue(type == preferenceVO.getType(), messageHelper.getMessage(
                        MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_TYPE_INVALID, preferenceVO.getType(), type)));
        Assert.isTrue(preferenceVO.getType().validate(preferenceVO.getValue()), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_VALUE_INVALID, preferenceVO.getValue(),
                preferenceVO.getType()));
    }

    private void validatePreferenceFields(final ContextualPreferenceVO preferenceVO) {
        validateName(preferenceVO.getName());
        validateResource(preferenceVO.getResource());
        Assert.notNull(preferenceVO.getValue(), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_VALUE_MISSING));
        Assert.notNull(preferenceVO.getType(), messageHelper.getMessage(
                MessageConstants.ERROR_CONTEXTUAL_PREFERENCE_TYPE_MISSING));
    }

    private Optional<PreferenceType> retrieveExpectedType(final ContextualPreferenceVO preferenceVO) {
        final List<ContextualPreference> sameContextualPreferences =
                contextualPreferenceDao.load(preferenceVO.getName());
        return sameContextualPreferences.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(sameContextualPreferences.get(0).getType());
    }

    private ContextualPreference preferenceFromVO(final ContextualPreferenceVO preferenceVO) {
        return new ContextualPreference(preferenceVO.getName(), preferenceVO.getValue(), preferenceVO.getType(),
                preferenceVO.getResource());
    }

    /**
     * Deletes contextual preference by the given preference parameters.
     *
     * @throws IllegalArgumentException if there is no preference with such parameters.
     */
    @Transactional
    public ContextualPreference delete(final String name, final ContextualPreferenceExternalResource resource) {
        validateName(name);
        validateResource(resource);
        final ContextualPreference preference = load(name, resource);
        contextualPreferenceDao.delete(name, resource);
        return preference;
    }
}
