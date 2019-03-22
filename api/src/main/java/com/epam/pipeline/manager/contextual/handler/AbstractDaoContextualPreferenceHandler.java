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
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstract contextual preference handler which uses contextual preferences table
 * to load contextual preferences from.
 */
abstract class AbstractDaoContextualPreferenceHandler extends AbstractContextualPreferenceHandler {

    private final ContextualPreferenceDao contextualPreferenceDao;

    AbstractDaoContextualPreferenceHandler(final ContextualPreferenceLevel level,
                                           final ContextualPreferenceHandler nextHandler,
                                           final ContextualPreferenceDao contextualPreferenceDao) {
        super(level, nextHandler);
        this.contextualPreferenceDao = contextualPreferenceDao;
    }

    @Override
    public Optional<ContextualPreference> search(final List<String> preferences,
                                                 final List<ContextualPreferenceExternalResource> resources) {
        final Optional<ContextualPreference> preference = resources.stream()
                .filter(res -> res.getLevel() == level)
                .findFirst()
                .map(res -> preferences.stream()
                        .map(pref -> loadPreference(pref, res))
                        .filter(Optional::isPresent)
                        .map(Optional::get))
                .flatMap(Stream::findFirst);
        return preference.isPresent()
                ? preference
                : searchUsingNextHandler(preferences, resources);
    }

    Optional<ContextualPreference> loadPreference(final String name,
                                                  final ContextualPreferenceExternalResource resource) {
        return contextualPreferenceDao.load(name, resource);
    }
}
