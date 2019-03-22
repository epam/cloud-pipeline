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

export default class FilterExpression {
  field;
  value;
  operand;
  filterExpressionType;
  expressions;
  options;
  static types = {
    LOGICAL: 'LOGICAL',
    AND: 'AND',
    OR: 'OR'
  };
  constructor (field, value, type, operand, leftExpression, rightExpression, options) {
    this.field = field;
    this.value = value;
    this.filterExpressionType = type;
    this.options = options;
    this.operand = operand;
    if (leftExpression) {
      this.expressions = [leftExpression];
      if (rightExpression) {
        this.expressions.push(rightExpression);
      }
    }
  }

  toStringExpression () {
    return '';
  }

  findElementAtPosition (position) {
    if (this.expressions) {
      for (let i = 0; i < this.expressions.length; i++) {
        const result = this.expressions[i].findElementAtPosition(position);
        if (result) {
          return result;
        }
      }
    }
    if (this.options) {
      const check = (field, includeLast = false) => {
        return this.options[field] && this.options[field].first_column !== undefined &&
          this.options[field].last_column !== undefined &&
          this.options[field].first_column <= position &&
          ((this.options[field].last_column > position && !includeLast) ||
          (this.options[field].last_column >= position && includeLast));
      };
      if (check('property')) {
        return {
          text: this.field,
          isProperty: true,
          expression: this,
          starts: this.options.property.first_column,
          ends: this.options.property.last_column
        };
      } else if (check('operand')) {
        return {
          text: this.operand,
          isOperand: true,
          expression: this,
          starts: this.options.operand.first_column,
          ends: this.options.operand.last_column
        };
      } else if (check('value')) {
        return {
          text: this.value,
          isValue: true,
          expression: this,
          starts: this.options.value.first_column,
          ends: this.options.value.last_column
        };
      } else if (check('total', true) && this.options.operand) {
        return {
          text: this.value || '',
          isValue: true,
          expression: this,
          starts: this.options.operand.last_column + 1,
          ends: this.options.total.last_column
        };
      }
    }
    return null;
  }
}
