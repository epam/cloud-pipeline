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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Contextual preferences reducer that can reduce contextual preferences with comma-separated values.
 */
public class ArrayContextualPreferenceReducer implements ContextualPreferenceReducer {

    private static final String DELIMITER = ",";

    @Override
    public Optional<ContextualPreference> reduce(final List<ContextualPreference> preferences) {
        if (preferences.isEmpty()) {
            return Optional.empty();
        }
        final String mergedValue = preferences.stream()
                .map(ContextualPreference::getValue)
                .flatMap(instanceTypes -> Arrays.stream(instanceTypes.split(DELIMITER)))
                .distinct()
                .collect(Collectors.joining(DELIMITER));
        final ContextualPreference reducedPreference =
                preferences.get(0)
                        .withValue(mergedValue)
                        .withCreatedDate(null)
                        .withResource(null);
        return Optional.of(reducedPreference);
    }
}
