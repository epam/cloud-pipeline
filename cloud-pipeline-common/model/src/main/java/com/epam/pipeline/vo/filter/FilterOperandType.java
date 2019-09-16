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

package com.epam.pipeline.vo.filter;

import java.util.HashMap;
import java.util.Map;

public enum FilterOperandType {
    LESS("<"),
    LESS_OR_EQUALS("<="),
    EQUALS("="),
    NOT_EQUALS("!="),
    MORE_OR_EQUALS(">="),
    MORE(">");

    private String operand;

    private static Map<String, FilterOperandType> operandsMap = new HashMap<>();
    static {
        operandsMap.put(LESS.operand, LESS);
        operandsMap.put(LESS_OR_EQUALS.operand, LESS_OR_EQUALS);
        operandsMap.put(EQUALS.operand, EQUALS);
        operandsMap.put(NOT_EQUALS.operand, NOT_EQUALS);
        operandsMap.put(MORE_OR_EQUALS.operand, MORE_OR_EQUALS);
        operandsMap.put(MORE.operand, MORE);
    }

    FilterOperandType(String operand) {
        this.operand = operand;
    }

    public static FilterOperandType getByString(String operand) {
        if (operand == null) {
            return null;
        }
        return operandsMap.get(operand);
    }


    public String getOperand() {
        return this.operand;
    }
}
