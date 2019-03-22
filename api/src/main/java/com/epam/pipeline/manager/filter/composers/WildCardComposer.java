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


import com.epam.pipeline.manager.filter.LogicalExpression;
import com.epam.pipeline.manager.filter.WrongFilterException;

public class WildCardComposer extends AbstractFilterComposer {
    @Override
    public String generateExpression(LogicalExpression expression) throws WrongFilterException {
        String sqlExpression;
        switch (expression.getOperandType()) {
            case EQUALS:
                sqlExpression = String.format("(lower(%s) like lower(:%s))",
                        expression.getDatabaseFieldName(),
                        expression.getDatabaseValuePlaceholder());
                break;
            case NOT_EQUALS:
                sqlExpression = String.format("(lower(%s) not like lower(:%s))",
                        expression.getDatabaseFieldName(),
                        expression.getDatabaseValuePlaceholder());
                break;
            default:
                throw new WrongFilterException(
                        String.format("Supported operands for '%s' field are: '=', '!='",
                                expression.getField()));
        }
        return sqlExpression;
    }
}
