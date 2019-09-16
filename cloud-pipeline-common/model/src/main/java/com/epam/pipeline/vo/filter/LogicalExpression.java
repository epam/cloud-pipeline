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
import com.epam.pipeline.vo.filter.converters.AbstractFilterValueConverter;
import com.epam.pipeline.vo.filter.converters.DateConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class LogicalExpression extends FilterExpression {

    private String databaseFieldName;
    private String databaseValuePlaceholder;
    private List<String> databaseValuePlaceholders;
    private boolean multiplePlaceholders;

    private AbstractFilterComposer composer;

    private LogicalExpression() {
        super();
    }

    LogicalExpression(String field, String value, FilterOperandType operand) {
        this();
        this.setField(field);
        this.setValue(value);
        this.setOperandType(operand);
    }

    public String generateSQLStatement() throws WrongFilterException {
        if (StringUtils.isEmpty(this.databaseFieldName)
                || StringUtils.isEmpty(this.databaseValuePlaceholder)) {
            throw new WrongFilterException();
        }
        return String.format("(%s %s :%s)",
                this.databaseFieldName,
                this.getOperandType().getOperand(),
                this.databaseValuePlaceholder);
    }

    @Override
    public String toSQLStatement() throws WrongFilterException {
        if (this.composer == null) {
            throw new WrongFilterException();
        }
        return this.composer.generateExpression(this);
    }

    private String getValuePlaceholder(Field field,
                                       Map<String, Long> parametersPlaceholders) {
        String placeholder = field.getName();
        if (!parametersPlaceholders.containsKey(placeholder)) {
            parametersPlaceholders.put(placeholder, 0L);
        } else {
            Long placeholderCount = parametersPlaceholders.get(placeholder) + 1;
            parametersPlaceholders.replace(placeholder, placeholderCount);
            placeholder = String.format("%s_%d", placeholder, placeholderCount);
        }
        return placeholder;
    }

    private void associateFilterField(Field field,
                                      FilterField filterField,
                                      MapSqlParameterSource parameterSource,
                                      Map<String, Long> parametersPlaceholders,
                                      Map<String, Object> params)
            throws WrongFilterException {
        if (!Arrays.asList(filterField.supportedOperands()).contains(this.getOperandType())) {
            final String supportedOperands = String.join(
                    ", ",
                    Arrays.stream(filterField.supportedOperands())
                            .map(operand -> String.format("'%s'", operand.getOperand()))
                            .collect(Collectors.toList())
            );
            throw new WrongFilterException(
                    String.format(
                            "Operand '%s' is not supported for field '%s'. Supported operands are: %s",
                            this.getOperandType().getOperand(),
                            this.getField(),
                            supportedOperands
                    )
            );
        }
        String dbFieldName = filterField.databaseFieldName();
        if (StringUtils.isEmpty(dbFieldName)) {
            dbFieldName = field.getName();
        }
        if (StringUtils.isEmpty(filterField.databaseTableAlias())) {
            this.databaseFieldName = dbFieldName;
        } else {
            this.databaseFieldName = String.format("%s.%s",
                    filterField.databaseTableAlias(),
                    dbFieldName);
        }

        Object value;
        AbstractFilterValueConverter converter = null;
        try {
            Constructor<? extends AbstractFilterValueConverter> constructor = filterField.converter()
                    .getConstructor();
            converter = constructor.newInstance();
        } catch (NoSuchMethodException |
                IllegalAccessException |
                InvocationTargetException |
                InstantiationException e) {
            log.error(e.getMessage(), e);
        }
        if (converter != null) {
            value = converter.convert(
                    this.getField(),
                    this.getValue(),
                    this.getOperandType(),
                    params);
        } else {
            value = this.getValue();
        }

        this.multiplePlaceholders = filterField.multiplePlaceholders() && value instanceof Collection<?>;
        if (this.multiplePlaceholders) {
            this.databaseValuePlaceholders = new ArrayList<>();
            for (Object valueItem : (Collection<?>) value) {
                String placeholder = this.getValuePlaceholder(field, parametersPlaceholders);
                this.databaseValuePlaceholders.add(placeholder);
                parameterSource.addValue(placeholder, valueItem);
            }
        } else {
            this.databaseValuePlaceholder = this.getValuePlaceholder(field, parametersPlaceholders);
            parameterSource.addValue(this.databaseValuePlaceholder, value);
        }

        try {
            Constructor<? extends AbstractFilterComposer> constructor = filterField.composer()
                    .getConstructor();
            this.composer = constructor.newInstance();
        } catch (NoSuchMethodException |
                IllegalAccessException |
                InvocationTargetException |
                InstantiationException e) {
            log.error(e.getMessage(), e);
        }
        this.setAssociatedFilterField(filterField);
    }

    private FilterExpression preProcessField(Field field,
                                             FilterField filterField,
                                             Class context,
                                             MapSqlParameterSource parameterSource,
                                             Map<String, Long> parametersPlaceholders,
                                             Map<String, Object> params)
            throws WrongFilterException {
        if (DateConverter.class.isAssignableFrom(filterField.converter()) &&
                (
                        this.getOperandType() == FilterOperandType.EQUALS ||
                                this.getOperandType() == FilterOperandType.NOT_EQUALS
                )) {
            if (this.getOperandType() == FilterOperandType.EQUALS) {
                AndFilterExpression andFilterExpression = new AndFilterExpression();
                List<FilterExpression> expressionList = new ArrayList<>();
                LogicalExpression leftExpression = new LogicalExpression(
                        this.getField(),
                        this.getValue(),
                        FilterOperandType.MORE_OR_EQUALS);
                LogicalExpression rightExpression = new LogicalExpression(
                        this.getField(),
                        this.getValue(),
                        FilterOperandType.LESS_OR_EQUALS);
                expressionList.add(leftExpression);
                expressionList.add(rightExpression);
                andFilterExpression.setExpressions(expressionList);
                return andFilterExpression.preProcessExpression(
                        context,
                        parameterSource,
                        parametersPlaceholders,
                        params);
            } else {
                OrFilterExpression orFilterExpression = new OrFilterExpression();
                List<FilterExpression> expressionList = new ArrayList<>();
                LogicalExpression leftExpression = new LogicalExpression(
                        this.getField(),
                        this.getValue(),
                        FilterOperandType.MORE);
                LogicalExpression rightExpression = new LogicalExpression(
                        this.getField(),
                        this.getValue(),
                        FilterOperandType.LESS);
                expressionList.add(leftExpression);
                expressionList.add(rightExpression);
                orFilterExpression.setExpressions(expressionList);
                return orFilterExpression.preProcessExpression(
                        context,
                        parameterSource,
                        parametersPlaceholders,
                        params);
            }
        } else {
            this.associateFilterField(field, filterField, parameterSource, parametersPlaceholders, params);
            return this;
        }
    }

    public boolean filterFieldMatches(FilterField filterField) {
        return Pattern.matches(filterField.displayName(), this.getField()) ||
                filterField.displayName().equalsIgnoreCase(this.getField());
    }

    @Override
    public FilterExpression preProcessExpression(Class context,
                                                 MapSqlParameterSource parameterSource,
                                                 Map<String, Long> parametersPlaceholders,
                                                 Map<String, Object> params)
            throws WrongFilterException {
        Field[] fields = context.getFields();
        for (Field field : fields) {
            FilterFields filterFields = field.getAnnotation(FilterFields.class);
            if (filterFields != null) {
                for (FilterField filterField : filterFields.value()) {
                    if (this.filterFieldMatches(filterField)) {
                        return this.preProcessField(
                                field,
                                filterField,
                                context,
                                parameterSource,
                                parametersPlaceholders,
                                params);
                    }
                }
            } else {
                FilterField filterField = field.getAnnotation(FilterField.class);
                if (filterField != null && this.filterFieldMatches(filterField)) {
                    return this.preProcessField(
                            field,
                            filterField,
                            context,
                            parameterSource,
                            parametersPlaceholders,
                            params);
                }
            }
        }
        throw new WrongFilterException(String.format("Unknown field: %s", this.getField()));
    }
}
