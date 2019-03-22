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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default contextual preference reducer that only reduces preferences specified in the {@link #preferenceReducerMap}
 * using corresponding reducer from that map.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultContextualPreferenceReducer implements ContextualPreferenceReducer {

    private final MessageHelper messageHelper;
    private final Map<String, ContextualPreferenceReducer> preferenceReducerMap;

    @Override
    public Optional<ContextualPreference> reduce(final List<ContextualPreference> preferences) {
        if (preferences.isEmpty()) {
            return Optional.empty();
        }
        final ContextualPreference validPreference = preferences.get(0);
        if (preferences.size() == 1) {
            return Optional.of(validPreference);
        }
        final List<ContextualPreference> invalidPreferences = preferences.stream()
                .filter(preference -> !preference.getName().equals(validPreference.getName())
                        || preference.getResource().getLevel() != validPreference.getResource().getLevel()
                        || preference.getType() != validPreference.getType())
                .collect(Collectors.toList());
        return invalidPreferences.isEmpty()
                ? reduceValidPreferences(preferences)
                : reduceInvalidPreferences(validPreference, invalidPreferences);
    }

    private Optional<ContextualPreference> reduceValidPreferences(final List<ContextualPreference> preferences) {
        return preferenceReducerMap.getOrDefault(preferences.get(0).getName(), fallbackEmptyReducer())
                .reduce(preferences);
    }

    private ContextualPreferenceReducer fallbackEmptyReducer() {
        return preferences -> {
            log.warn(messageHelper.getMessage(MessageConstants.WARN_CONTEXTUAL_PREFERENCE_REDUCER_NOT_FOUND,
                    preferences));
            return Optional.empty();
        };
    }

    private Optional<ContextualPreference> reduceInvalidPreferences(
            final ContextualPreference validPreference,
            final List<ContextualPreference> invalidPreferences) {
        log.warn(messageHelper.getMessage(MessageConstants.WARN_CONTEXTUAL_PREFERENCE_REDUCING_FAILED, validPreference,
                invalidPreferences));
        return Optional.empty();
    }
}
