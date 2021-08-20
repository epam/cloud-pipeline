/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Modal,
  Input,
  Row
} from 'antd';
import PropTypes from 'prop-types';
import styles from './ShareItemInfoDialog.css';

class ShareItemInfoDialog extends React.Component {
  state = {
    copySuccess: false,
    copyDisabled: false
  }

  get itemName () {
    const {file} = this.props;
    if (file && file.name) {
      return file.name;
    }
    return '';
  }

  copyUrlToClipboard = (event) => {
    const {sharedUrl} = this.props;
    event && event.stopPropagation();
    if (navigator && navigator.clipboard) {
      navigator.clipboard.writeText(sharedUrl).then(() => {
        this.setState({copySuccess: true});
      });
    } else {
      this.setState({copyDisabled: true});
    }
  };

  onOk = () => {
    const {onOk} = this.props;
    onOk && onOk();
  };

  render () {
    const {
      title,
      visible,
      sharedUrl
    } = this.props;
    const {copySuccess, copyDisabled} = this.state;
    const modalFooter = (
      <Row>
        <Button
          onClick={this.onOk}
        >
          Close
        </Button>
      </Row>
    );
    return (
      <Modal
        visible={visible}
        title={title}
        onCancel={this.onOk}
        footer={modalFooter}
        closable={false}
        maskClosable={false}
      >
        <div className={styles.container}
        >
          <span className={styles.mainText}>
            {`Link ${this.itemName ? `to ${this.itemName}` : ''} created.`}
          </span>
          <span className={styles.hint}>
            Make sure you copy the link below
          </span>
          <div className={styles.urlSection}>
            <Input
              value={sharedUrl}
              style={{
                width: '100%'
              }}
            />
            <Button
              type={copySuccess ? '' : 'primary'}
              onClick={this.copyUrlToClipboard}
              disabled={copySuccess || copyDisabled}
              style={{
                marginLeft: '15px',
                minWidth: '130px'
              }}
            >
              {copySuccess ? 'Successfully copied' : 'Copy to clipboard'}
            </Button>
          </div>
        </div>
      </Modal>
    );
  }
}

ShareItemInfoDialog.PropTypes = {
  title: PropTypes.oneOfType(PropTypes.bool, PropTypes.string),
  visible: PropTypes.bool,
  sharedUrl: PropTypes.string,
  onOk: PropTypes.func,
  file: PropTypes.object
};

export default ShareItemInfoDialog;
