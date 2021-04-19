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
import Conflicts from './conflicts';
import styles from './conflicts.css';

class ConflictsDialog extends React.Component {
  render () {
    const {
      conflicts,
      run,
      storage,
      visible
    } = this.props;
    return (
      <Modal
        title="Resolve conflicts"
        closable={false}
        visible={visible}
        width="98%"
        style={{
          top: 10
        }}
        footer={(
          <div
            className={styles.dialogActions}
          >
            <Button
              type="danger"
            >
              ABORT
            </Button>
            <Button
              type="primary"
            >
              RESOLVE
            </Button>
          </div>
        )}
      >
        <Conflicts
          conflicts={conflicts}
          run={run}
          storage={storage}
        />
      </Modal>
    );
  }
}

ConflictsDialog.propTypes = {
  conflicts: PropTypes.arrayOf(PropTypes.string),
  onClose: PropTypes.func,
  run: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storage: PropTypes.object,
  visible: PropTypes.bool
};

export default ConflictsDialog;
