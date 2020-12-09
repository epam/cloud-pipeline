/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster.pool.filter.value;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Getter
public class MapValueMatcher implements ValueMatcher<Map<String, String>> {

    private final Map<String, String> value;

    @Override
    public boolean matches(final Map<String, String> anotherValue) {
        final Map<String, String> currentValue = getValue();
        if (Objects.isNull(currentValue) || Objects.isNull(anotherValue)) {
            return false;
        }
        return anotherValue.entrySet()
                .stream()
                .allMatch(anotherEntry -> {
                    final String value = currentValue.get(anotherEntry.getKey());
                    if (Objects.isNull(value)) {
                        return false;
                    }
                    return Objects.equals(value, anotherEntry.getValue());
                });
    }

    /**
     * @param anotherValue
     * @return {@code true} if current map does not contain any value for
     * any of {@param anotherValue} keys
     */
    @Override
    public boolean empty(final Map<String, String> anotherValue) {
        final Map<String, String> currentValue = getValue();
        if (Objects.isNull(currentValue)) {
            return Objects.nonNull(anotherValue);
        }
        return MapUtils.emptyIfNull(anotherValue)
                .keySet()
                .stream()
                .allMatch(key -> {
                    final String value = currentValue.get(key);
                    return StringUtils.isBlank(value);
                });
    }
}
