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

package com.epam.pipeline.manager.filter;

import java.util.List;
import java.util.stream.Collectors;

public class AndFilterExpression extends FilterExpression {

    AndFilterExpression() {
        super();
    }

    AndFilterExpression(List<FilterExpression> expressions) {
        this.setExpressions(expressions);
    }

    @Override
    public String toSQLStatement() throws WrongFilterException {
        if (this.getExpressions() != null ) {
            return String.format("(%s)",
                    this.getExpressions().stream().map(filterExpression -> {
                        try {
                            return filterExpression.toSQLStatement();
                        } catch (WrongFilterException e) {
                            throw new IllegalStateException(e);
                        }
                    }).collect(Collectors.joining(" AND ")));
        }
        throw new WrongFilterException();
    }
}
