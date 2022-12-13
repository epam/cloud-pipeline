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
import {
  Modal,
  Checkbox,
  Button,
  Spin
} from 'antd';
import styles from './controls.css';

class ExportSearchResultsDialog extends React.Component {
  state = {
    keysToExport: []
  }

  componentDidMount () {
    this.setDefaultKeysToExport();
  }

  componentDidUpdate (prevProps, prevState) {
    if (prevProps.columns !== this.props.columns) {
      this.setDefaultKeysToExport();
    }
  }

  setDefaultKeysToExport = () => {
    this.setState({keysToExport: this.columns.map(({key}) => key)});
  };

  get columns () {
    const {columns} = this.props;
    if (columns && columns.length > 0) {
      return columns.map(({key, name}) => ({
        key,
        value: key,
        label: name
      }));
    }
    return [];
  }

  onSelectedItemsChange = (values) => {
    this.setState({keysToExport: values});
  };

  handleOk = () => {
    const {onOk} = this.props;
    const {keysToExport} = this.state;
    onOk && onOk(keysToExport);
  };

  clearSelection = () => {
    this.setState({keysToExport: []});
  };

  render () {
    const {
      visible,
      onCancel,
      pending,
      columns
    } = this.props;
    const {keysToExport} = this.state;
    if (!columns) {
      return null;
    }
    return (
      <Modal
        visible={visible}
        onCancel={onCancel}
        title="Export CSV fields"
        footer={[
          <Button
            key="back"
            onClick={onCancel}
          >
            CANCEL
          </Button>,
          <Button
            disabled={keysToExport.length === 0 || pending}
            key="download"
            type="primary"
            onClick={this.handleOk}
          >
            DOWNLOAD CSV
          </Button>
        ]}
      >
        <Spin spinning={pending}>
          <div className={styles.exportContainer}>
            <div className={styles.controls}>
              <Button
                size="small"
                onClick={this.setDefaultKeysToExport}
              >
                Select all
              </Button>
              <Button
                style={{marginLeft: '10px'}}
                size="small"
                onClick={this.clearSelection}
              >
                Clear selection
              </Button>
            </div>
            <Checkbox.Group
              value={keysToExport}
              options={this.columns}
              onChange={this.onSelectedItemsChange}
              className={styles.checkboxGroup}
            />
          </div>
        </Spin>
      </Modal>
    );
  }
}

ExportSearchResultsDialog.propTypes = {
  visible: PropTypes.bool,
  pending: PropTypes.bool,
  columns: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    name: PropTypes.string
  })),
  onOk: PropTypes.func,
  onCancel: PropTypes.func
};

export default ExportSearchResultsDialog;
