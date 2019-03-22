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

package com.epam.pipeline.manager.filter.composers;


import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.LogicalExpression;
import com.epam.pipeline.manager.filter.WrongFilterException;

import java.util.ArrayList;
import java.util.List;

public class PipelineRunParameterComposer extends AbstractFilterComposer {
    @Override
    public String generateExpression(LogicalExpression expression) throws WrongFilterException {
        if (expression.getOperandType() != FilterOperandType.EQUALS &&
                expression.getOperandType() != FilterOperandType.NOT_EQUALS) {
            throw new WrongFilterException("Supported operands for '%s' field are: '=', '!='");
        }
        if (expression.isMultiplePlaceholders()) {
            List<String> expressions = new ArrayList<>();
            String joiningOperand = expression.getOperandType() == FilterOperandType.EQUALS
                    ? " or " : " and ";
            for (String placeholder : expression.getDatabaseValuePlaceholders()) {
                if (expression.getOperandType() == FilterOperandType.EQUALS) {
                    expressions.add(
                            String.format("(%s like :%s)",
                                    expression.getDatabaseFieldName(),
                                    placeholder)
                    );
                } else {
                    expressions.add(
                            String.format("(%s not like :%s)",
                                    expression.getDatabaseFieldName(),
                                    placeholder)
                    );
                }
            }
            return String.format("(%s)", String.join(joiningOperand, expressions));
        } else {
            if (expression.getOperandType() == FilterOperandType.EQUALS) {
                return String.format("(%s like :%s)",
                        expression.getDatabaseFieldName(),
                        expression.getDatabaseValuePlaceholder());
            } else {
                return String.format("(%s not like :%s)",
                        expression.getDatabaseFieldName(),
                        expression.getDatabaseValuePlaceholder());
            }
        }
    }
}
