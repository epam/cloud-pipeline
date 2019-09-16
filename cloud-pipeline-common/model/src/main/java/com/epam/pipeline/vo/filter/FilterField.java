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

import com.epam.pipeline.vo.filter.composers.AbstractFilterComposer;
import com.epam.pipeline.vo.filter.composers.DefaultComposer;
import com.epam.pipeline.vo.filter.converters.AbstractFilterValueConverter;
import com.epam.pipeline.vo.filter.converters.DefaultConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(FilterFields.class)
public @interface FilterField {
    String displayName() default "";
    String databaseFieldName() default "";
    String databaseTableAlias() default "";
    String description() default "";
    AclExpressionType aclField() default AclExpressionType.NONE;
    boolean isRegex() default false;
    boolean multiplePlaceholders() default false;
    FilterOperandType[] supportedOperands() default {
            FilterOperandType.LESS,
            FilterOperandType.LESS_OR_EQUALS,
            FilterOperandType.EQUALS,
            FilterOperandType.NOT_EQUALS,
            FilterOperandType.MORE_OR_EQUALS,
            FilterOperandType.MORE
    };
    Class<? extends AbstractFilterValueConverter> converter() default DefaultConverter.class;
    Class<? extends AbstractFilterComposer> composer() default DefaultComposer.class;
}
