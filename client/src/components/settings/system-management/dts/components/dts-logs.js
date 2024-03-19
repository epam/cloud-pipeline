/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {observer} from 'mobx-react';
import {message, Alert, Spin} from 'antd';
import dataStorageAvailable from '../../../../../models/dataStorage/DataStorageAvailable';
import DataStorageItemSize from '../../../../../models/dataStorage/DataStorageItemSize';
import {createObjectStorageWrapper} from '../../../../../utils/object-storage';
import RunTaskLogs from '../../../../runs/run-task-logs';
import styles from './dts-logs.css';

const LOGS_FILE_NAME = 'dts.log';

const KB = 1024;
const MB = 1024 * KB;
const SIZE_THRESHOLD_MB = 50;
const SIZE_THRESHOLD_B = SIZE_THRESHOLD_MB * MB;

@observer
class DtsLogs extends React.Component {
  state = {
    sizeExceeded: false,
    logsString: undefined,
    pending: false
  }

  @observable
  storage;

  @observable
  relativePathToFile;

  componentDidMount () {
    this.fetchLogs();
  }

  componentDidUpdate (prevProps) {
    if (this.props.folder !== prevProps.folder) {
      this.fetchLogs();
    }
  }

  @computed
  get downloadAvailable () {
    return this.storage && this.relativePathToFile;
  }

  fetchLogs = () => {
    const {folder} = this.props;
    if (!folder) {
      return;
    }
    const pathToFile = `${folder}/${LOGS_FILE_NAME}`;
    const checkFile = async (pathToFile) => {
      const fileSizeRequest = new DataStorageItemSize();
      await fileSizeRequest.send([pathToFile]);
      const {size} = (fileSizeRequest.value || [])[0] || {};
      if (fileSizeRequest.loaded) {
        return {
          size,
          sizeExceeded: size > SIZE_THRESHOLD_B
        };
      }
      return {size: undefined, sizeExceeded: false};
    };
    this.setState({pending: true}, async () => {
      await dataStorageAvailable.fetchIfNeededOrWait();
      this.storage = await createObjectStorageWrapper(
        dataStorageAvailable.value,
        folder
      );
      if (!this.storage) {
        return this.setState({
          error: `Storage ${folder} not found.`,
          pending: false
        });
      }
      const {size, sizeExceeded} = await checkFile(pathToFile);
      if (size !== undefined && sizeExceeded) {
        return this.setState({sizeExceeded, pending: false});
      }
      if (size === undefined) {
        this.setState({error: 'Log file not found.', pending: false});
        return message.error('Log file not found.', 5);
      }
      this.relativePathToFile = this.storage.getRelativePath(pathToFile);
      const content = await this.storage.getFileContent(this.relativePathToFile);
      this.setState({
        logsString: content,
        pending: false
      });
    });
  };

  downloadLogsFile = () => {
    if (this.state.pending) {
      return;
    }
    this.setState({pending: true}, async () => {
      const path = await this.storage.generateFileUrl(this.relativePathToFile);
      const a = document.createElement('a');
      a.href = path;
      a.download = LOGS_FILE_NAME;
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      this.setState({pending: false});
    });
  };

  render () {
    const {logsString, pending, sizeExceeded, error} = this.state;
    if (error) {
      return (
        <Alert
          message={error}
          type="error"
        />
      );
    }
    if (!pending && sizeExceeded) {
      return (
        <Alert
          message={(
            <p>
              Logs size is more than <b>{SIZE_THRESHOLD_MB}mb</b> and cannot be viewed.
              <a
                style={{marginLeft: 5}}
                onClick={this.downloadLogsFile}
              >
                Download logs file.
              </a>
            </p>
          )}
          type="info"
        />
      );
    }
    if (!pending && !logsString) {
      return (
        <Alert
          message={(<p>Logs are not available or empty.</p>)}
          type="error"
        />
      );
    }
    return (
      <Spin spinning={pending}>
        <div className={styles.logsContainer}>
          <div className={styles.controls}>
            {this.downloadAvailable ? (
              <a onClick={this.downloadLogsFile}>Download logs</a>
            ) : null}
          </div>
          <RunTaskLogs
            className={styles.logsConsole}
            lineClassName={styles.consoleLine}
            logs={logsString}
            showDate={false}
            showLineNumber
            searchAvailable
          />
        </div>
      </Spin>
    );
  }
}

DtsLogs.propTypes = {
  folder: PropTypes.string
};

export default DtsLogs;
