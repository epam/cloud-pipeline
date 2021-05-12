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
import {
  Button,
  Modal
} from 'antd';
import VSVersions from '../../../vs-versions-select';
import styles from './checkout-dialog.css';

class CheckoutDialog extends React.Component {
  state = {
    version: undefined
  };

  componentDidMount () {
    this.clearVersion();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.visible !== this.props.visible ||
      prevProps.repository !== this.props.repository
    ) {
      this.clearVersion();
    }
  }

  clearVersion = () => {
    const {
      repository
    } = this.props;
    this.setState({
      version: repository ? repository.revision : undefined
    });
  }

  onChange = (version) => {
    this.setState({
      version
    });
  };

  onSelect = () => {
    const {onSelect} = this.props;
    const {version} = this.state;
    onSelect && onSelect(version);
  };

  render () {
    const {
      visible,
      onClose,
      repository
    } = this.props;
    const {
      version
    } = this.state;
    return (
      <Modal
        title="Select revision to checkout"
        visible={visible && !!repository}
        onCancel={onClose}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              id="cancel-checkout-button"
              onClick={onClose}
            >
              Cancel
            </Button>
            <Button
              id="do-checkout-button"
              type="primary"
              disabled={!version || version === repository?.revision}
              onClick={this.onSelect}
            >
              Change Revision
            </Button>
          </div>
        )}
      >
        <VSVersions
          className={styles.versionsSelect}
          dropdownMatchSelectWidth
          repository={repository ? repository.id : undefined}
          onChange={this.onChange}
          value={version}
        />
      </Modal>
    );
  }
}

CheckoutDialog.propTypes = {
  onClose: PropTypes.func,
  onSelect: PropTypes.func,
  repository: PropTypes.object,
  visible: PropTypes.bool
};

export default CheckoutDialog;
