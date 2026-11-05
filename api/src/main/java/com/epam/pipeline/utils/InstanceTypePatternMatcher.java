/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

public interface InstanceTypePatternMatcher {

    AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * Matches an instance type name against a comma-separated list of Ant-style patterns, the format
     * shared by {@code cluster.allowed.instance.types} and {@code cluster.capacity.reservation.instance.families}.
     * A blank pattern list means no restriction.
     */
    static boolean matches(final String instanceType, final String patterns) {
        if (StringUtils.isBlank(patterns)) {
            return true;
        }
        final List<String> allowedInstancePatterns = Arrays.asList(patterns.split(","));
        return allowedInstancePatterns.stream().anyMatch(pattern -> MATCHER.match(pattern, instanceType));
    }
}
