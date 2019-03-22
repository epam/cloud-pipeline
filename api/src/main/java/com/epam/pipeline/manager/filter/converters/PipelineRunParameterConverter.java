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


import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.WrongFilterException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PipelineRunParameterConverter extends AbstractFilterValueConverter {

    public String getParameterName(String field) {
        List<String> parts = Arrays.asList(field.split("\\."));
        if (parts.size() >= 2 && parts.get(0).equalsIgnoreCase("parameter")) {
            parts = parts.subList(1, parts.size());
        }
        return String.join(".", parts);
    }

    @Override
    public Object convert(String field,
                          String value,
                          FilterOperandType operandType,
                          Map<String, Object> params)
            throws WrongFilterException {
        return Arrays.asList(
                // only 1 parameter presented
                String.format("%s=%s", this.getParameterName(field), this.clearQuotes(value)),
                // first param with type
                String.format("%s=%s=%%", this.getParameterName(field), this.clearQuotes(value)),
                // not first param with type
                String.format("%%|%s=%s=%%", this.getParameterName(field), this.clearQuotes(value)),
                // first parameter
                String.format("%s=%s|%%", this.getParameterName(field), this.clearQuotes(value)),
                // last parameter
                String.format("%%|%s=%s", this.getParameterName(field), this.clearQuotes(value)),
                // parameter is not first or last in the list
                String.format("%%|%s=%s|%%", this.getParameterName(field), this.clearQuotes(value))
        );
    }
}
