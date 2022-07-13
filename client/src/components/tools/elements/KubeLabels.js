/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  Button,
  Col,
  Icon,
  Input,
  Checkbox,
  Row
} from 'antd';
import styles from './KubeLabels.css';

class KubeLabels extends React.Component {
  onAddNewLabel = () => {
    const {onAddLabel} = this.props;
    onAddLabel && onAddLabel({
      key: '',
      value: '',
      predefined: false
    });
  };

  onRemoveLabel = (index) => {
    const {onRemoveKubeLabel} = this.props;
    onRemoveKubeLabel && onRemoveKubeLabel(index);
  };

  onLabelChange = (labelData, index, field) => event => {
    const {onChange, labels} = this.props;
    const value = labelData.predefined
      ? event.target.checked
      : event.target.value;
    const currentLabel = labels[index];
    onChange && onChange(
      index,
      {...currentLabel, ...{[field]: value}},
      field
    );
  };

  renderLabelRow = (label, index) => {
    const {errors} = this.props;
    const hasError = errors && errors[label.key];
    return (
      <Row
        key={index}
        id={`kubeLabel_${label.key}`}
        type="flex"
        style={{marginBottom: 5, flexWrap: 'nowrap'}}
        align="top"
      >
        <Col
          span={8}
          style={{textAlign: 'right'}}
          className={styles.inputContainer}
        >
          <Input
            className={classNames(
              styles.labelInput,
              styles.keyInput,
              {'cp-error': hasError}
            )}
            value={label.key}
            onChange={this.onLabelChange(label, index, 'key')}
          />
          {
            hasError ? (
              <span
                className={classNames(styles.error, 'cp-error')}
              >
                {errors[label.key]}
              </span>
            ) : null
          }
        </Col>
        <Col
          span={14}
          className={styles.inputContainer}
        >
          <Input
            className={styles.labelInput}
            value={label.value}
            onChange={this.onLabelChange(label, index, 'value')}
          />
        </Col>
        <Col>
          <Icon
            id="remove-kubeLabel-button"
            className={classNames(
              'dynamic-delete-button',
              styles.removeButton
            )}
            type="minus-circle-o"
            onClick={() => this.onRemoveLabel(index)}
          />
        </Col>
      </Row>
    );
  };

  renderPredefinedLabelRow = (label, index) => {
    return (
      <Row
        key={label.key}
        id={`kubeLabel_${label.key}`}
        type="flex"
        style={{marginBottom: 5, flexWrap: 'nowrap'}}
        align="top"
      >
        <Checkbox
          checked={label.value}
          onChange={this.onLabelChange(label, index, 'value')}
        >
          {label.key}
        </Checkbox>
      </Row>
    );
  };

  render () {
    const {labels} = this.props;
    return (
      <div className={styles.container}>
        {(labels || []).map((label, index) => {
          return label.predefined
            ? this.renderPredefinedLabelRow(label, index)
            : this.renderLabelRow(label, index);
        })}
        <Button
          size="small"
          type="dashed"
          onClick={this.onAddNewLabel}
          className={styles.addButton}
        >
          + New Kube Label
        </Button>
      </div>
    );
  }
}

KubeLabels.propTypes = {
  labels: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    value: PropTypes.oneOfType([PropTypes.string, PropTypes.bool]),
    predefined: PropTypes.oneOfType([PropTypes.string, PropTypes.bool])
  })),
  onChange: PropTypes.func,
  onAddLabel: PropTypes.func,
  onRemoveKubeLabel: PropTypes.func,
  errors: PropTypes.shape({})
};

export default KubeLabels;
