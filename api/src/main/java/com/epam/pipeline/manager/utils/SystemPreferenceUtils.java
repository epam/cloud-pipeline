/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class contains util method somehow related to SystemPreference usage
 * */
@SuppressWarnings("HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SystemPreferenceUtils {

    private static final String PROJECT_INDICATOR_DELIMITER = "=";

    public static Set<Pair<String, String>> parseProjectIndicator(final String preference) {
        List<String> projectIndicator = Arrays.asList(preference.split(","));
        if (CollectionUtils.isEmpty(projectIndicator)) {
            return Collections.emptySet();
        }
        return projectIndicator
                .stream()
                .filter(indicator -> indicator.contains(PROJECT_INDICATOR_DELIMITER))
                .map(indicator -> {
                    String[] splittedProjectIndicator = indicator.split(PROJECT_INDICATOR_DELIMITER);
                    Assert.state(splittedProjectIndicator.length == 2,
                            "Invalid project indicator pair: " + indicator);
                    String key = splittedProjectIndicator[0];
                    String value = splittedProjectIndicator[1];
                    if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                        throw new IllegalArgumentException("Invalid project indicator pair.");
                    }
                    return new ImmutablePair<>(key, value);
                }).collect(Collectors.toSet());
    }
}
