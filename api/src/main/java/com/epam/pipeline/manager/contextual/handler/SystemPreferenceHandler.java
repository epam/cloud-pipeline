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

import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.manager.preference.PreferenceManager;
import java.util.List;
import java.util.Optional;

/**
 * System contextual preference handler.
 *
 * It handles preferences with {@link ContextualPreferenceLevel#SYSTEM} level.
 * Handler associates contextual preference with a {@link Preference} entity.
 *
 * Returns contextual preferences without external resource because system preferences
 * doesn't have external resources.
 */
public class SystemPreferenceHandler extends AbstractContextualPreferenceHandler {

    private final PreferenceManager preferenceManager;

    public SystemPreferenceHandler(final PreferenceManager preferenceManager,
                                   final ContextualPreferenceHandler nextHandler) {
        super(ContextualPreferenceLevel.SYSTEM, nextHandler);
        this.preferenceManager = preferenceManager;
    }

    public SystemPreferenceHandler(final PreferenceManager preferenceManager) {
        this(preferenceManager, null);
    }

    @Override
    boolean externalEntityExists(final ContextualPreference preference) {
        return false;
    }

    @Override
    public Optional<ContextualPreference> search(final List<String> preferences,
                                                 final List<ContextualPreferenceExternalResource> resources) {
        final Optional<ContextualPreference> preference = preferences.stream()
                .map(this::loadPreference)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        return preference.isPresent()
                ? preference
                : searchUsingNextHandler(preferences, resources);
    }

    private Optional<ContextualPreference> loadPreference(final String name) {
        return preferenceManager.load(name)
                .map(p -> new ContextualPreference(p.getName(), p.getValue(), p.getType()));
    }
}
