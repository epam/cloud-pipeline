/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Checkbox, Icon, Input} from 'antd';
import classNames from 'classnames';
import styles from '../../edit-hot-node-pool.css';

function LabelsControl ({
  labels,
  disabled,
  readOnly,
  error,
  onAdd,
  onRemove,
  onChangeName,
  onChangeValue,
  onChangeMonitored
}) {
  if (labels.length === 0 && readOnly) {
    return null;
  }
  const renderLabelControl = (label) => {
    const duplicate = labels
      // eslint-disable-next-line max-len
      .filter((lb) => (lb.name || '').trim().toLowerCase() === (label.name || '').trim().toLowerCase())
      .length > 1;
    const wrongName = (label.name || '').trim().length === 0;
    const st = {
      display: 'flex',
      flexDirection: 'row',
      alignItems: 'center',
      gap: 5,
      margin: '2px 0',
      lineHeight: '24px',
      width: '100%'
    };
    if (readOnly) {
      return (
        <div
          key={label.key}
          style={st}
        >
          <span style={{flexShrink: 0}}>
            {label.name}:
          </span>
          <span
            style={{
              flexShrink: 1,
              overflow: 'auto',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}
          >
            {label.value}
          </span>
          {
            label.monitored && (
              <span style={{flexShrink: 0}}>(monitored)</span>
            )
          }
        </div>
      );
    }
    return (
      <div
        key={label.key}
        style={st}
      >
        <Input
          className={classNames({'cp-error': duplicate || wrongName})}
          style={{flex: 1, marginRight: 5}}
          value={label.name}
          onChange={onChangeName(label.key)}
        />
        <Input
          style={{flex: 2, marginRight: 5}}
          value={label.value}
          onChange={onChangeValue(label.key)}
        />
        <Checkbox
          style={{flexShrink: 0, marginRight: 5}}
          checked={label.monitored}
          onChange={onChangeMonitored(label.key)}
        >
          Monitored
        </Checkbox>
        <Button size="small" onClick={onRemove(label.key)} type="danger">
          <Icon type="delete" />
        </Button>
      </div>
    );
  };
  return (
    <div>
      <div
        className={classNames(styles.formRow, styles.multiRow)}
      >
        <span className={styles.label}>
          Labels:
        </span>
        <div className={styles.column}>
          {labels.map(renderLabelControl)}
          {
            !readOnly && (
              <div>
                <Button
                  disabled={disabled}
                  onClick={onAdd}
                  type="dashed"
                >
                  <Icon type="plus" />
                  Add node label
                </Button>
              </div>
            )
          }
        </div>
      </div>
      {
        error && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {error}
          </div>
        )
      }
    </div>
  );
}

LabelsControl.propTypes = {
  labels: PropTypes.array,
  disabled: PropTypes.bool,
  readOnly: PropTypes.bool,
  error: PropTypes.string,
  onAdd: PropTypes.func,
  onRemove: PropTypes.func,
  onChangeName: PropTypes.func,
  onChangeValue: PropTypes.func,
  onChangeMonitored: PropTypes.func
};

LabelsControl.defaultProps = {
  labels: []
};

export default LabelsControl;
