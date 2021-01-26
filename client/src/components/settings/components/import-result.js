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
import hljs from 'highlight.js';
import styles from './import-users.css';

function processBashScript (script) {
  let command = hljs.highlight('bash', script).value;
  const r = /\[URL\](.+)\[\/URL\]/ig;
  let e = r.exec(command);
  while (e) {
    command = command.substring(0, e.index) +
      `<a href="${e[1]}" target="_blank">${e[1]}</a>` +
      command.substring(e.index + e[0].length);
    e = r.exec(command);
  }
  return command;
}

function mapLogItem (log) {
  return `[${log.created}] ${log.status} ${log.message}`;
}

class ImportResult extends React.Component {
  render () {
    const {
      logs,
      onClose,
      visible
    } = this.props;
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
        <pre style={{width: '100%', maxHeight: '70vh', overflow: 'auto', fontSize: 'smaller'}}>
          <code
            id="users-import-logs"
            dangerouslySetInnerHTML={{
              __html: processBashScript(logs.map(mapLogItem).join('\n'))
            }} />
        </pre>
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
}

ImportResult.propTypes = {
  logs: PropTypes.array,
  onClose: PropTypes.func,
  visible: PropTypes.bool
};

export default ImportResult;
