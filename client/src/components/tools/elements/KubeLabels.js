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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
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

function kubeLabelArraysEqual (source = [], compare = []) {
  for (let i = 0; i < source.length; i++) {
    const {key, value} = source[i];
    const compareItem = compare.find(o => o.key === key);
    if (!compareItem || compareItem.value !== value) {
      return false;
    }
  }
  return true;
}

function kubeLabelsHasChanges (initialData = [], actualData = []) {
  const actualDataFiltered = actualData
    .filter(o => !o.predefined || `${o.value}` === 'true')
    .map(o => {
      if (o.predefined) {
        return {
          ...o,
          value: 'true'
        };
      }
      return o;
    });
  return !kubeLabelArraysEqual(initialData, actualDataFiltered) ||
    !kubeLabelArraysEqual(actualDataFiltered, initialData);
}

function prepareKubeLabelsPayload (labels) {
  return (labels || [])
    .filter(label => !label.predefined || (label.predefined && `${label.value}` === 'true'))
    .reduce((acc, label) => {
      acc[label.key] = label.value;
      return acc;
    }, {});
}

@inject('preferences')
@observer
class KubeLabels extends React.Component {
  state = {
    errors: {}
  }

  @computed
  get predefinedKeys () {
    const {preferences} = this.props;
    if (preferences && preferences.loaded) {
      return preferences.toolPredefinedKubeLabels || [];
    }
    return [];
  }

  get labels () {
    const {labels = []} = this.props;
    const predefinedKeys = this.predefinedKeys;
    let keys = [...new Set((predefinedKeys || []).map(key => key.trim()))]
      .filter(Boolean);
    const predefinedLabels = keys.map(key => {
      const existedData = labels.find(label => label.key === key);
      return {
        key,
        value: existedData ? `${existedData.value}` === 'true' : false,
        predefined: true
      };
    });
    const userLabels = labels.map((label) => {
      const predefined = keys.includes(label.key);
      if (predefined) {
        keys.splice(keys.indexOf(label.key), 1);
        return undefined;
      }
      return {
        ...label,
        predefined: false
      };
    }).filter(Boolean);
    return [...predefinedLabels, ...userLabels];
  }

  onAddNewLabel = () => {
    const blankLabel = {
      key: '',
      value: '',
      predefined: false
    };
    this.submitChanges([...this.labels, blankLabel]);
  };

  onRemoveLabel = (labelIndex) => {
    const filteredLabels = [...this.labels]
      .filter((label, index) => index !== labelIndex);
    this.submitChanges(filteredLabels);
  };

  onLabelChange = (label, index, field) => event => {
    const value = label.predefined
      ? event.target.checked
      : event.target.value;
    const labels = [...this.labels];
    const currentLabel = labels[index];
    if (currentLabel) {
      labels[index] = {
        ...labels[index],
        [field]: value
      };
      this.submitChanges(labels);
    }
  };

  submitChanges = (labels) => {
    const {onChange} = this.props;
    const errors = this.validateLabels(labels);
    this.setState({errors}, () => {
      onChange && onChange(labels, errors);
    });
  };

  validateLabels = (labels = []) => {
    const errors = {};
    for (let i = 0; i < labels.length; i++) {
      const currentKey = labels[i].key;
      const hasDuplicates = labels
        .filter(filtered => filtered.key === currentKey).length > 1;
      if (!currentKey) {
        errors[currentKey] = 'Key is required';
        continue;
      }
      if (hasDuplicates) {
        errors[currentKey] = 'Key should be unique';
      }
    }
    return errors;
  };

  renderLabelRow = (label, index) => {
    const {errors} = this.state;
    const hasError = errors && errors[label.key];
    return (
      <Row
        key={index}
        id={`kubeLabel_${label.key}`}
        type="flex"
        style={{flexWrap: 'nowrap'}}
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
    return (
      <div className={styles.container}>
        {this.labels.map((label, index) => {
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
          + New runtime label
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
  onChange: PropTypes.func
};

export {kubeLabelsHasChanges, prepareKubeLabelsPayload};
export default KubeLabels;
