/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {Icon, Button, Input, Select} from 'antd';
import styles from './exclude-parameters-control.css';

const OPERATORS = {
  equals: 'EQUAL',
  notEquals: 'NOT_EQUAL'
};

const OPERATOR_NAMES = {
  [OPERATORS.equals]: 'Equals',
  [OPERATORS.notEquals]: 'Not equals'
};

function ExcludeParametersControl ({
  parameters,
  pending,
  onChange,
  onAdd,
  onRemove
}) {
  const handleChange = (field, value, index) => {
    const current = parameters[index];
    if (current && onChange) {
      current[field] = value;
      onChange({...current}, index);
    }
  };

  const onAddParameter = () => {
    onAdd && onAdd({
      name: undefined,
      value: undefined,
      operator: OPERATORS.equals
    });
  };

  const onRemoveParameter = index => {
    onRemove && onRemove(index);
  };

  return (
    <div className={styles.container}>
      {(parameters || []).map((parameter, index) => (
        <div
          className={styles.controlsContainer}
          key={index}
        >
          <Input
            value={parameter.name}
            className={styles.control}
            placeholder="Parameter name"
            onChange={({target}) => handleChange('name', target.value, index)}
            disabled={pending}
          />
          <Select
            value={parameter.operator}
            onChange={(value) => handleChange('operator', value, index)}
            disabled={pending}
            className={classNames(
              styles.control,
              styles.select
            )}
          >
            {Object.values(OPERATORS).map(operator => (
              <Select.Option
                value={operator}
                key={operator}
              >
                {OPERATOR_NAMES[operator]}
              </Select.Option>
            ))}
          </Select>
          <Input
            value={parameter.value}
            className={styles.control}
            placeholder="Parameter value"
            onChange={({target}) => handleChange('value', target.value, index)}
            disabled={pending}
          />
          <Button
            onClick={() => onRemoveParameter(index)}
            className={classNames(styles.control, styles.removeBtn)}
            disabled={pending}
            type="danger"
          >
            <Icon type="delete" />
          </Button>
        </div>
      ))}
      <Button
        onClick={onAddParameter}
        style={{width: '140px'}}
        disabled={pending}
      >
        <Icon type="plus" />
        Add parameter
      </Button>
    </div>
  );
}

ExcludeParametersControl.propTypes = {
  parameters: PropTypes.arrayOf(PropTypes.object),
  pending: PropTypes.bool,
  onChange: PropTypes.func,
  onAdd: PropTypes.func,
  onRemove: PropTypes.func
};

export default ExcludeParametersControl;
