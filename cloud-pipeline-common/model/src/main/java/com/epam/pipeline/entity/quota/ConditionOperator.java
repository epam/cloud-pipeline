/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.entity.quota;

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Operators supported in compute quota rule filter expressions.
 */
@Getter
public enum ConditionOperator {

    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_OR_EQUALS(">="),
    LESS("<"),
    LESS_OR_EQUALS("<=");

    private final String symbol;

    ConditionOperator(final String symbol) {
        this.symbol = symbol;
    }

    private static final Map<String, ConditionOperator> BY_SYMBOL;

    static {
        final Map<String, ConditionOperator> map = new HashMap<>();
        for (final ConditionOperator op : values()) {
            map.put(op.symbol, op);
        }
        BY_SYMBOL = Collections.unmodifiableMap(map);
    }

    public static ConditionOperator fromSymbol(final String symbol) {
        final ConditionOperator op = BY_SYMBOL.get(symbol);
        if (op == null) {
            throw new IllegalArgumentException("Unknown operator: '" + symbol + "'");
        }
        return op;
    }
}
