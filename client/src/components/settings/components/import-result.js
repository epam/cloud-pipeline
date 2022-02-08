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
import {Alert, Modal} from 'antd';
import styles from './import-users.css';
import BashCode from '../../special/bash-code';

function mapLogItem (log) {
  return `[${log.created}] ${log.status} ${log.message}`;
}

function ImportResult (
  {
    logs,
    onClose,
    visible,
    mode
  }
) {
  let content;
  if (!logs || !logs.length) {
    content = (
      <Alert
        type="info"
        message="No logs are available"
      />
    );
  } else {
    content = (
      <BashCode
        id="users-import-logs"
        style={{
          width: '100%',
          maxHeight: '70vh',
          overflow: 'auto'
        }}
        code={logs.map(mapLogItem).join('\n')}
      />
    );
  }
  if (/^inline$/i.test(mode)) {
    return (
      <div className={styles.logs}>
        {content}
      </div>
    );
  }
  return (
    <Modal
      onCancel={onClose}
      footer={null}
      title="Import logs"
      visible={visible}
      width="80%"
    >
      <div className={styles.logs}>
        {content}
      </div>
    </Modal>
  );
}

ImportResult.propTypes = {
  logs: PropTypes.array,
  onClose: PropTypes.func,
  visible: PropTypes.bool,
  mode: PropTypes.oneOf(['inline', 'modal'])
};

ImportResult.defaultProps = {
  mode: 'modal'
};

export default ImportResult;
