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

package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.user.Role;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

/**
 * Role contextual preference handler.
 *
 * It handles preferences with {@link ContextualPreferenceLevel#ROLE} level.
 * Handler associates preference with several role {@link Role} entities.
 */
public class RoleContextualPreferenceHandler extends AbstractDaoContextualPreferenceHandler {

    private final RoleDao roleDao;
    private final ContextualPreferenceReducer reducer;

    public RoleContextualPreferenceHandler(final RoleDao roleDao,
                                           final ContextualPreferenceDao contextualPreferenceDao,
                                           final ContextualPreferenceHandler nextHandler,
                                           final ContextualPreferenceReducer reducer) {
        super(ContextualPreferenceLevel.ROLE, nextHandler, contextualPreferenceDao);
        this.roleDao = roleDao;
        this.reducer = reducer;
    }

    public RoleContextualPreferenceHandler(final RoleDao roleDao,
                                           final ContextualPreferenceDao contextualPreferenceDao,
                                           final ContextualPreferenceReducer reducer) {
        this(roleDao, contextualPreferenceDao, null, reducer);
    }

    @Override
    boolean externalEntityExists(final ContextualPreference preference) {
        return roleDao.loadRole(Long.valueOf(preference.getResource().getResourceId())).isPresent();
    }

    @Override
    public Optional<ContextualPreference> search(final List<String> preferences,
                                                 final List<ContextualPreferenceExternalResource> resources) {
        final List<ContextualPreferenceExternalResource> roleResources = resources.stream()
                .filter(res -> res.getLevel() == level)
                .collect(Collectors.toList());
        final Optional<ContextualPreference> reducedPreference = preferences.stream()
                .map(pref -> roleResources.stream()
                        .map(res -> loadPreference(pref, res))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))
                .filter(CollectionUtils::isNotEmpty)
                .findFirst()
                .flatMap(reducer::reduce);
        return reducedPreference.isPresent()
                ? reducedPreference
                : searchUsingNextHandler(preferences, resources);
    }
}
