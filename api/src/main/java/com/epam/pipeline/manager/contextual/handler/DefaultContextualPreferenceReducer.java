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
import java.util.Set;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.preference.PreferenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default contextual preference reducer that only reduces preferences specified in {@link #preferenceNameToReducer}
 * or {@link #preferenceTypeToReducer} using corresponding reducer from one of the maps.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultContextualPreferenceReducer implements ContextualPreferenceReducer {

    private final MessageHelper messageHelper;
    private final Map<String, ContextualPreferenceReducer> preferenceNameToReducer;
    private final Map<PreferenceType, ContextualPreferenceReducer> preferenceTypeToReducer;

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
        return getReducer(preferences.get(0)).reduce(preferences);
    }

    private ContextualPreferenceReducer getReducer(final ContextualPreference preference) {
        return Optional.ofNullable(preferenceNameToReducer.get(preference.getName())).map(Optional::of)
                .orElseGet(() -> Optional.ofNullable(preferenceTypeToReducer.get(preference.getType())))
                .orElseGet(this::defaultReducer);
    }

    private ContextualPreferenceReducer defaultReducer() {
        return preferences -> {
            final Set<String> values = preferences.stream()
                    .map(ContextualPreference::getValue)
                    .collect(Collectors.toSet());
            if (values.size() > 1) {
                log.warn(messageHelper.getMessage(MessageConstants.WARN_CONTEXTUAL_PREFERENCE_DIFFERENT_VALUES,
                    preferences));
                return Optional.empty();
            }
            return Optional.of(preferences.get(0));
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
