/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.sync.model;


import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class TransferMatcher {

    private final Integer searchDepth;
    private final String expression;

    public TransferMatcher(final String expression) {
        this.expression = expression;
        this.searchDepth = calculateExpressionSearchDepth(expression);
    }

    private Integer calculateExpressionSearchDepth(final String expression) {
        final List<String> expressionChunks = Optional.ofNullable(expression)
            .map(string -> string.split("/"))
            .map(Stream::of)
            .orElseGet(Stream::empty)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
        return expressionChunks.contains("**") ? Integer.MAX_VALUE : expressionChunks.size();
    }
}
