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

package com.epam.pipeline.entity.preference;

import com.epam.pipeline.config.JsonMapper;
import java.util.Arrays;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;

@RequiredArgsConstructor
public enum PreferenceType {
    STRING(0, value -> true),
    INTEGER(1, NumberUtils::isDigits),
    FLOAT(2, NumberUtils::isNumber),
    BOOLEAN(3, value -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)),
    OBJECT(4, JsonMapper::isNullOrAnyValidJson),
    LONG(5, NumberUtils::isDigits);

    @Getter
    private final long id;
    private final Predicate<String> validator;

    public static PreferenceType getById(Long id) {
        return Arrays.stream(values())
                .filter(value -> value.id == id)
                .findFirst()
                .orElse(null);
    }

    public boolean validate(final String value) {
        return validator.test(value);
    }
}
