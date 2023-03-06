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
import {Alert, Button, Modal, Select} from 'antd';
import styles from './correct-inputs-modal.css';

function inputsArraysAreEqual (a, b) {
  const aa = [...(new Set(a || []))].sort();
  const bb = [...(new Set(b || []))].sort();
  if (aa.length !== bb.length) {
    return false;
  }
  for (let i = 0; i < aa.length; i += 1) {
    if (aa[i] !== bb[i]) {
      return false;
    }
  }
  return true;
}

class CorrectInputsModal extends React.Component {
  state = {
    correction: {}
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      (
        prevProps.visible !== this.props.visible &&
        this.props.visible
      ) ||
      !inputsArraysAreEqual(prevProps.inputs, this.props.inputs) ||
      !inputsArraysAreEqual(prevProps.availableInputs, this.props.availableInputs)
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      inputs = [],
      availableInputs = []
    } = this.props;
    this.setState({
      correction: inputs
        .map((input) => ({
          [input]: availableInputs.includes(input) ? input : undefined
        }))
        .reduce((r, c) => ({...r, ...c}), {})
    });
  };

  onCorrectClicked = () => {
    const {
      onCorrect
    } = this.props;
    if (typeof onCorrect === 'function') {
      const {correction = {}} = this.state;
      onCorrect(correction);
    }
  };

  setCorrectionValue = (input) => (value) => this.setState({
    correction: {
      ...this.state.correction,
      [input]: value
    }
  });

  render () {
    const {
      className,
      style,
      visible,
      onCancel,
      inputs,
      availableInputs
    } = this.props;
    const {
      correction
    } = this.state;
    const getValueForInput = (input) => correction[input];
    return (
      <Modal
        className={className}
        style={style}
        visible={visible}
        onCancel={onCancel}
        maskClosable={false}
        closable={false}
        width={600}
        title="Correct inputs for pipeline"
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              onClick={this.onCorrectClicked}
            >
              CORRECT
            </Button>
          </div>
        )}
      >
        <Alert
          showIcon
          type="info"
          style={{marginBottom: 5}}
          message={(
            <p>
              There are inputs (image channels)
              that are not available for the current image.<br />
              Pick the available channel or leave it blank
              (you won't be able to submit analysis until
              correct values are provided).
            </p>
          )}
        />
        {
          inputs.map((input) => (
            <div
              key={input}
              className={styles.inputRow}
            >
              <span
                className={styles.input}
              >
                {input}:
              </span>
              <Select
                allowClear
                className={styles.selector}
                value={getValueForInput(input)}
                onChange={this.setCorrectionValue(input)}
              >
                {
                  availableInputs.map((value) => (
                    <Select.Option key={value} value={value}>
                      {value}
                    </Select.Option>
                  ))
                }
              </Select>
            </div>
          ))
        }
      </Modal>
    );
  }
}

CorrectInputsModal.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  onCorrect: PropTypes.func,
  onCancel: PropTypes.func,
  visible: PropTypes.bool,
  inputs: PropTypes.oneOfType(PropTypes.object, PropTypes.arrayOf(PropTypes.string)),
  availableInputs: PropTypes.oneOfType(PropTypes.object, PropTypes.arrayOf(PropTypes.string))
};

export default CorrectInputsModal;
