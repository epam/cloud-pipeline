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

package com.epam.pipeline.manager.filter.converters;

import com.amazonaws.util.StringUtils;
import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.WrongFilterException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractListConverter<T> extends AbstractFilterValueConverter {

    String clearQuotes(String value) {
        if (!StringUtils.isNullOrEmpty(value) && ((value.startsWith("(") && value.endsWith(")")) ||
                (value.startsWith("[") && value.endsWith("]")))) {
            value = value.substring(1, value.length() - 1);
        }
        return super.clearQuotes(value);
    }

    public abstract T convertItem(String item);

    @Override
    public Object convert(
            String field,
            String value,
            FilterOperandType operandType,
            Map<String, Object> params) throws WrongFilterException {
        if (StringUtils.isNullOrEmpty(value)) {
            throw new WrongFilterException(String.format("Value should not be empty for field '%s'", field));
        }
        return Arrays.stream(this.clearQuotes(value).split("[,;]"))
                .map(String::trim)
                .map(this::clearQuotes)
                .map(this::convertItem)
                .collect(Collectors.toList());
    }
}
