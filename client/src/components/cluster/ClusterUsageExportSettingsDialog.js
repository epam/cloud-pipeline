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
import {
  Button,
  Modal,
  Select
} from 'antd';
import styles from './ClusterUsageExportSettingsDialog.css';

class ClusterUsageExportSettingsDialog extends React.Component {
  state = {
    mode: 'XLS',
    tick: undefined
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.clearState();
    }
  }

  mouseMoveListener = e => {
    e.preventDefault();
    e.stopPropagation();
  };

  clearState = () => {
    this.setState({
      mode: 'XLS',
      tick: undefined
    });
  };

  onExportClicked = () => {
    const {mode, tick} = this.state;
    const {onExport} = this.props;
    onExport && onExport(mode, tick);
  };

  onModeChanged = e => {
    this.setState({
      mode: e
    });
  };

  onTickChanged = e => {
    this.setState({
      tick: e
    });
  };

  render () {
    const {
      availableIntervals,
      disabled,
      onCancel,
      visible
    } = this.props;
    const {
      mode,
      tick
    } = this.state;
    return (
      <Modal
        title="Export settings"
        visible={visible}
        onCancel={onCancel}
        closable={!disabled}
        maskClosable={!disabled}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              disabled={disabled}
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disabled}
              type="primary"
              onClick={this.onExportClicked}
            >
              EXPORT
            </Button>
          </div>
        )}
      >
        <div className={styles.formItem}>
          <span className={styles.label}>
            Format:
          </span>
          <Select
            disabled={disabled}
            style={{flex: 1}}
            value={mode}
            onChange={this.onModeChanged}
          >
            <Select.Option key="XLS" value="XLS">
              Excel
            </Select.Option>
            <Select.Option key="CSV" value="CSV">
              CSV
            </Select.Option>
          </Select>
        </div>
        <div className={styles.formItem}>
          <span className={styles.label}>
            Ticks:
          </span>
          <Select
            disabled={disabled}
            allowClear
            style={{flex: 1}}
            value={tick}
            onChange={this.onTickChanged}
          >
            {
              (availableIntervals || []).map(interval => (
                <Select.Option key={interval.value} value={interval.value}>
                  {interval.name}
                </Select.Option>
              ))
            }
          </Select>
        </div>
      </Modal>
    );
  }
}

ClusterUsageExportSettingsDialog.propTypes = {
  availableIntervals: PropTypes.array,
  disabled: PropTypes.bool,
  onCancel: PropTypes.func,
  onExport: PropTypes.func,
  visible: PropTypes.bool
};

export default ClusterUsageExportSettingsDialog;
