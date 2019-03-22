/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import styles from './IssueComment.css';
import {Row, Progress} from 'antd';

const WAITING = 0;
const UPLOADING = 1;
const ERROR = 2;
const DONE = 3;
const CANCELLED = 4;

export default class FileDropContainer extends React.Component {

  static propTypes = {
    action: PropTypes.string,
    onFilesLoaded: PropTypes.func
  };

  state = {
    dragOver: false,
    percent: 0,
    error: undefined,
    status: WAITING,
    done: true,
    progressHidden: true
  };

  onDragOver = (event) => {
    event.preventDefault();
    if (!this.state.dragOver) {
      this.setState({dragOver: true});
    }
  };

  onDragLeave = () => {
    this.setState({dragOver: false});
  };

  onDrop = (event) => {
    event.preventDefault();
    const files = [];
    if (event.dataTransfer.items) {
      // Use DataTransferItemList interface to access the file(s)
      for (let i = 0; i < event.dataTransfer.items.length; i++) {
        // If dropped items aren't files, reject them
        if (event.dataTransfer.items[i].kind === 'file') {
          files.push(event.dataTransfer.items[i].getAsFile());
        }
      }
    } else {
      // Use DataTransfer interface to access the file(s)
      for (let i = 0; i < event.dataTransfer.files.length; i++) {
        files.push(event.dataTransfer.files[i]);
      }
    }
    this.setState({dragOver: false}, () => this.uploadFiles(files));
  };

  uploadFiles = async (files) => {
    const updatePercent = ({loaded, total}) => {
      this.setState({
        percent: Math.min(100, Math.ceil(loaded / total * 100)),
        status: UPLOADING
      });
    };
    const updateError = (error) => {
      this.setState({
        percent: 100,
        error: error || 'Error uploading files',
        status: ERROR,
        done: true
      });
    };
    const updateStatus = (status) => {
      let done = false;
      if (status === CANCELLED || status === ERROR || status === DONE) {
        done = true;
      }
      this.setState({
        percent: 100,
        status: status,
        done
      });
    };
    const onFilesLoaded = (payload) => {
      if (payload && payload.length) {
        for (let i = 0; i < payload.length; i++) {
          const uploadedFile = payload[i];
          const [file] = files.filter(f => f.name === uploadedFile.name);
          if (file) {
            uploadedFile.type = file.type;
          }
        }
        this.props.onFilesLoaded && this.props.onFilesLoaded(payload);
      }
      setTimeout(() => {
        this.setState({
          progressHidden: true
        });
      }, 500);
    };

    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      formData.append(file.name, file);
    }
    const request = new XMLHttpRequest();
    request.withCredentials = true;
    request.upload.onprogress = function (event) {
      updatePercent(event);
    };
    request.upload.onload = function (event) {
      updateStatus(DONE);
    };
    request.upload.onerror = function (event) {
      updateError();
    };
    request.onreadystatechange = function () {
      if (request.readyState !== 4) return;

      if (request.status !== 200) {
        updateError(request.statusText);
        onFilesLoaded(null);
      } else {
        try {
          const response = JSON.parse(request.responseText);
          if (response.status && response.status.toLowerCase() === 'error') {
            updateError(response.message);
            onFilesLoaded(null);
          } else {
            updateStatus(DONE);
            onFilesLoaded(response.payload);
          }
        } catch (e) {
          updateError(`Error parsing response: ${e.toString()}`);
          onFilesLoaded(null);
        }
      }
    };
    this.setState({
      progressHidden: false
    }, () => {
      request.open('POST', this.props.action);
      request.send(formData);
    });
  };

  getProgressStatus = () => {
    switch (this.state.status) {
      case ERROR: return 'error';
      case DONE: return 'success';
      default:
        return undefined;
    }
  };

  render () {
    return (
      <div
        style={{position: 'relative'}}
        className={this.state.dragOver ? styles.dragOver : undefined}
        onDrop={this.onDrop}
        onDragLeave={this.onDragLeave}
        onDragOver={this.onDragOver}>
        {this.props.children}
        {
          !this.state.progressHidden &&
          <Row type="flex" className={styles.uploadOverlay} align="middle" justify="center">
            <div
              style={{
                backgroundColor: '#ccc',
                opacity: 0.125,
                width: '100%',
                height: '100%',
                position: 'absolute'
              }}>
              {'\u00A0'}
            </div>
            <Progress
              type="circle"
              width={50}
              status={this.getProgressStatus()}
              percent={this.state.percent} />
          </Row>
        }
      </div>
    );
  }
}
