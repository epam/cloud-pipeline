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

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
class AclExpression extends LogicalExpression {
    private AclExpressionType aclExpressionType;

    AclExpression(AclExpressionType aclExpressionType, String value) {
        super(aclExpressionType.getAclType(), value, FilterOperandType.EQUALS);
        this.setAclExpressionType(aclExpressionType);
    }

    AclExpression(AclExpressionType aclExpressionType, List<Long> value) {
        super(
                aclExpressionType.getAclType(),
                String.join(
                        ", ",
                        value
                                .stream()
                                .map(Object::toString)
                                .collect(Collectors.toList())
                ),
                FilterOperandType.EQUALS);
        this.setAclExpressionType(aclExpressionType);
    }

    @Override
    public boolean filterFieldMatches(FilterField filterField) {
        return filterField.aclField() == this.getAclExpressionType();
    }
}
