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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class FilterExpression {

    private String field;
    private String value;
    private String operand;
    @JsonIgnore
    private FilterOperandType operandType;
    @JsonIgnore
    private FilterField associatedFilterField;

    private List<FilterExpression> expressions;
    private FilterExpressionType filterExpressionType;

    public String toSQLStatement() throws WrongFilterException {
        throw new WrongFilterException();
    }

    public FilterExpression preProcessExpression(Class context,
                                                 MapSqlParameterSource parameterSource,
                                                 Map<String, Long> parametersPlaceholders,
                                                 Map<String, Object> params)
            throws WrongFilterException {
        if (this.expressions != null) {
            List<FilterExpression> childExpressions = new ArrayList<>();
            for (FilterExpression childExpression : this.expressions) {
                childExpressions.add(
                        childExpression.preProcessExpression(
                                context,
                                parameterSource,
                                parametersPlaceholders,
                                params)
                );
            }
            this.expressions = childExpressions;
        }
        return this;
    }

    static FilterExpression generate(FilterExpression rootExpression,
                                     List<Long> allowedPipelines,
                                     String ownership)
            throws WrongFilterException {
        boolean containsOwnershipFilter = StringUtils.isNotEmpty(ownership);
        boolean containsPipelineIdsFilter = allowedPipelines != null && allowedPipelines.size() > 0;
        if (containsPipelineIdsFilter && containsOwnershipFilter) {
            AndFilterExpression mainExpression = new AndFilterExpression();
            OrFilterExpression aclExpression = new OrFilterExpression();
            aclExpression.setExpressions(Arrays.asList(
                    new AclExpression(AclExpressionType.PIPELINE_IDS, allowedPipelines),
                    new AclExpression(AclExpressionType.OWNERSHIP, ownership)
            ));
            mainExpression.setExpressions(Arrays.asList(
                    FilterExpression.prepare(rootExpression),
                    aclExpression
            ));
            return mainExpression;
        } else if (containsOwnershipFilter) {
            AndFilterExpression mainExpression = new AndFilterExpression();
            mainExpression.setExpressions(Arrays.asList(
                    FilterExpression.prepare(rootExpression),
                    new AclExpression(AclExpressionType.OWNERSHIP, ownership)
            ));
            return mainExpression;
        } else if (containsPipelineIdsFilter) {
            AndFilterExpression mainExpression = new AndFilterExpression();
            mainExpression.setExpressions(Arrays.asList(
                    FilterExpression.prepare(rootExpression),
                    new AclExpression(AclExpressionType.PIPELINE_IDS, allowedPipelines)
            ));
            return mainExpression;
        } else {
            return FilterExpression.prepare(rootExpression);
        }
    }

    private static FilterExpression prepare(FilterExpression expression) throws WrongFilterException {
        FilterExpression result = null;
        if (expression.getFilterExpressionType() == null) {
            throw new WrongFilterException("Unknown expression type");
        }
        switch (expression.filterExpressionType) {
            case LOGICAL:
                if (FilterOperandType.getByString(expression.getOperand()) == null) {
                    throw new WrongFilterException(
                            String.format("Unknown or incorrect operand: '%s'",
                                    expression.getOperand())
                    );
                }
                result = new LogicalExpression(
                        expression.getField(),
                        expression.getValue(),
                        FilterOperandType.getByString(expression.getOperand()));
                break;
            case OR:
                result = new OrFilterExpression();
                break;
            case AND:
                result = new AndFilterExpression();
                break;
            default:
                break;
        }
        if (expression.getExpressions() != null && result != null) {
            result.setExpressions(new ArrayList<>());
            for (FilterExpression child : expression.getExpressions()) {
                result.getExpressions().add(FilterExpression.prepare(child));
            }
        }
        return result;
    }

}
