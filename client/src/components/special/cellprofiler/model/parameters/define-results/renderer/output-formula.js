/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Button, Icon, Input, Select} from 'antd';
import classNames from 'classnames';
import {
  getObjectProperties, ObjectProperty,
  ObjectPropertyName
} from '../object-properties';
import {getObjectPropertyFunction, PropertyFunctionNames} from '../property-functions';
import styles from './define-results.css';

function split (expression) {
  if (!expression) {
    return [];
  }
  const result = [];
  // eslint-disable-next-line max-len
  const regExp = /\s*(\(|\)|\*|\/|\+|-|:|\^|[A-Za-z\d.,]+)\s*/ig;
  let e = regExp.exec(expression);
  while (e) {
    result.push(e[1]);
    e = regExp.exec(expression);
  }
  return result;
}

function parseExpression (parts = []) {
  if (parts.length === 0) {
    return [];
  }
  let i = 0;
  const variables = new Set();
  const parse = (root = true) => {
    if (parts.length <= i) {
      return [];
    }
    const result = [];
    while (i < parts.length) {
      const part = parts[i];
      i += 1;
      switch (part.toLowerCase()) {
        case '(':
          if (result.length > 0 && !result[result.length - 1].operator) {
            result.push({operator: true, value: '*'});
          }
          result.push({value: parse(false)});
          break;
        case ')':
          if (root) {
            throw new Error('Wrong expression');
          }
          return result;
        case '+':
        case '-':
        case ':':
        case '*':
        case '/':
        case '^':
          result.push({operator: true, value: part});
          break;
        default:
          const e = /^([^A-Za-z]+)([A-Za-z]+)/.exec(part);
          if (e) {
            const number = e[1];
            const variable = e[2];
            result.push({value: number});
            result.push({value: '*', operator: true});
            result.push({value: variable});
            variables.add(variable);
          } else {
            result.push({value: part});
            if (Number.isNaN(Number(part))) {
              variables.add(part);
            }
          }
          break;
      }
    }
    return result;
  };
  const parsed = parse();
  const validate = (parsedExpression) => {
    if (typeof parsedExpression === 'string') {
      return true;
    }
    if (parsedExpression.length % 2 !== 1) {
      throw new Error('Wrong expression');
    }
    if (parsedExpression[0].operator) {
      throw new Error('Wrong expression');
    }
    if (
      typeof parsedExpression[0].value !== 'string' &&
      Array.isArray(parsedExpression[0].value)
    ) {
      validate(parsedExpression[0].value);
    }
    for (let e = 1; e < parsedExpression.length - 1; e += 2) {
      if (!parsedExpression[e] || !parsedExpression[e + 1]) {
        throw new Error('Wrong expression');
      }
      if (!parsedExpression[e].operator || parsedExpression[e + 1].operator) {
        throw new Error('Wrong expression');
      }
      validate(parsedExpression[e + 1].value);
    }
    return true;
  };
  validate(parsed);
  const extract = (parsedExpression) => {
    if (typeof parsedExpression === 'string') {
      return parsedExpression;
    }
    if (typeof parsedExpression === 'object' && parsedExpression.value) {
      return extract(parsedExpression.value);
    }
    const isArray = o => typeof o !== 'string' && Array.isArray(o);
    const result = [...parsedExpression].map(o => o.value);
    if (isArray(result) && result.length === 1) {
      return extract(result[0]);
    }
    const extractByOperator = (...operator) => {
      const idx = result.findIndex(o => operator.includes(o));
      if (idx > 0) {
        let left = result[idx - 1];
        if (isArray(left)) {
          left = extract(left);
        }
        let right = result[idx + 1];
        if (isArray(right)) {
          right = extract(right);
        }
        result.splice(idx - 1, 3, {left, right, operator: result[idx]});
        extractByOperator(...operator);
      }
    };
    extractByOperator('^');
    extractByOperator('*', '/');
    extractByOperator('+', '-');
    return result[0];
  };
  const wrap = (part) => {
    if (typeof part === 'string') {
      return part;
    }
    const {
      left,
      right,
      operator
    } = part;
    return [wrap(left), operator, wrap(right)];
  };
  return {expression: wrap(extract(parsed)), variables: [...variables]};
}

function parseFormula (formula) {
  try {
    return parseExpression(split(formula));
  } catch (e) {
    return {error: e.message};
  }
}

function getVariableOptions (pipeline) {
  if (!pipeline) {
    return [];
  }
  const objects = pipeline.objects || [];
  return objects
    .map(object => {
      const spot = pipeline.getObjectIsSpot(object);
      const spotWithParent = spot && pipeline.getObjectIsSpotWithParent(object);
      const hasChild = pipeline.getObjectHasSpots(object);
      const props = getObjectProperties({spot, spotWithParent, hasChild});
      return props
        .map(prop => {
          if (prop === ObjectProperty.numberOfObjects) {
            return [{object, property: prop}];
          }
          return getObjectPropertyFunction(prop)
            .map(stat => ({object, property: prop, function: stat}));
        })
        .reduce((r, c) => ([...r, ...c]), []);
    })
    .reduce((r, c) => ([...r, ...c]), []);
}

function getVariableOptionValue (option) {
  if (!option) {
    return undefined;
  }
  return `${option.object}/${option.property}/${option.function || ''}`;
}

function OutputFormula (props) {
  const {
    formula,
    pipeline,
    onChange = () => {},
    onRemove = () => {}
  } = props || {};
  if (!formula || !pipeline) {
    return null;
  }
  const {
    name,
    value,
    variables = {}
  } = formula;
  const onChangeFormulaName = (e) => {
    onChange({
      ...formula,
      name: e.target.value
    });
  };
  const onChangeFormula = (e) => {
    const {
      expression,
      variables: parsedVariables = [],
      error: parsingError
    } = parseFormula(e.target.value);
    const newVariables = parsingError
      ? variables
      : parsedVariables
        .map(variable => ({[variable]: variables[variable]}))
        .reduce((r, c) => ({...r, ...c}), {});
    onChange({
      ...formula,
      expression,
      variables: newVariables,
      value: e.target.value,
      error: parsingError
    });
  };
  const {error} = parseFormula(value);
  const sortedVariables = Object.keys(variables || {}).sort();
  const properties = getVariableOptions(pipeline);
  const getProperty = value => properties.find(o => getVariableOptionValue(o) === value);
  const onChangeFormulaVariable = (variable) => (property) => {
    onChange({
      ...formula,
      variables: {
        ...variables,
        [variable]: getProperty(property)
      }
    });
  };
  return (
    <div>
      <div
        className={styles.row}
      >
        <span className={styles.title}>
          Formula:
        </span>
        <Input
          className={
            classNames(
              styles.value,
              {'cp-error': !!error}
            )
          }
          value={value}
          onChange={onChangeFormula}
        />
        <Button
          type="danger"
          size="small"
          style={{marginLeft: 5}}
          onClick={onRemove}
        >
          <Icon type="delete" />
        </Button>
      </div>
      {
        sortedVariables.map((variable) => (
          <div
            key={variable}
            className={styles.row}
            style={{paddingRight: 33}}
          >
            <span className={styles.title}>
              Variable <b>{variable}</b>:
            </span>
            <Select
              showSearch
              value={getVariableOptionValue(variables[variable])}
              className={styles.value}
              onChange={onChangeFormulaVariable(variable)}
              filterOption={(input, option) =>
                [option.props.object, option.props.property, option.props.statistics]
                  .filter(Boolean)
                  .some(o => o.toLowerCase().includes((input || '').toLowerCase()))
              }
            >
              {
                properties.map(property => (
                  <Select.Option
                    key={getVariableOptionValue(property)}
                    value={getVariableOptionValue(property)}
                    object={property.object}
                    property={ObjectPropertyName[property.property]}
                    statistics={
                      property.function
                        ? PropertyFunctionNames[property.function]
                        : undefined
                    }
                    title={
                      `${property.object}: ${ObjectPropertyName[property.property]}`
                        .concat(
                          property.function
                            ? ` - ${PropertyFunctionNames[property.function]}`
                            : ''
                        )
                    }
                  >
                    <b>{property.object}</b>: {ObjectPropertyName[property.property]}
                    {
                      property.function && (
                        <span style={{whiteSpace: 'pre'}}>
                          {' - '}
                          {PropertyFunctionNames[property.function]}
                        </span>
                      )
                    }
                  </Select.Option>
                ))
              }
            </Select>
          </div>
        ))
      }
      <div
        className={styles.row}
        style={{paddingRight: 33}}
      >
        <span className={styles.title}>
          Name:
        </span>
        <Input
          className={styles.value}
          value={name}
          onChange={onChangeFormulaName}
        />
      </div>
    </div>
  );
}

OutputFormula.propTypes = {
  formula: PropTypes.object,
  pipeline: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func
};

export default observer(OutputFormula);
