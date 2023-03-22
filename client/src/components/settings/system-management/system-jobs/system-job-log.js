/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, message, Modal} from 'antd';
import RunTaskLogs, {downloadTaskLogs} from '../../../runs/run-task-logs';
import styles from './system-jobs.css';

function SystemJobLog (props) {
  const {
    className,
    onClose,
    visible,
    runId,
    taskName,
    autoUpdate
  } = props;
  const onDownloadClicked = async () => {
    const hide = message.loading('Exporting logs...', 0);
    try {
      await downloadTaskLogs(runId, taskName);
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
    }
  };
  return (
    <Modal
      className={className}
      onCancel={onClose}
      visible={visible}
      footer={(
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'flex-end'
          }}
        >
          <Button
            style={{marginLeft: 5}}
            onClick={onDownloadClicked}
          >
            DOWNLOAD
          </Button>
          <Button
            style={{marginLeft: 5}}
            onClick={onClose}
          >
            CLOSE
          </Button>
        </div>
      )}
      bodyStyle={{padding: 0, margin: 0}}
      title={
        runId
          ? (<span><b>#{runId} job logs</b></span>)
          : false
      }
      width="80%"
    >
      {
        visible && (
          <RunTaskLogs
            className={styles.runLogs}
            runId={runId}
            taskName={taskName}
            autoUpdate={autoUpdate}
            showDate={false}
            showLineNumber
            searchAvailable
          />
        )
      }
    </Modal>
  );
}

SystemJobLog.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  runId: PropTypes.number,
  taskName: PropTypes.string,
  autoUpdate: PropTypes.bool
};

export default SystemJobLog;
