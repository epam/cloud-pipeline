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
import {Row, Upload, Button, Icon, Modal, Progress, Col, Tooltip} from 'antd';
import DataStorageGenerateUploadUrl from '../../models/dataStorage/DataStorageGenerateUploadUrl';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;
const MAX_S3_FILE_SIZE_GB = 5;
const MAX_NFS_FILE_SIZE_MB = 500;

export default class UploadButton extends React.Component {

  static propTypes = {
    action: PropTypes.string,
    multiple: PropTypes.bool,
    onRefresh: PropTypes.func,
    synchronous: PropTypes.bool,
    uploadToS3: PropTypes.bool,
    uploadToNFS: PropTypes.bool,
    title: PropTypes.string,
    validate: PropTypes.func
  };

  state = {
    uploadInfoVisible: false,
    uploadInfoClosable: false,
    uploadingFiles: [],
    synchronousUploadingFiles: []
  };

  showUploadInfo = (files) => {
    this.setState({
      uploadInfoVisible: true,
      uploadInfoClosable: false,
      uploadingFiles: files
    });
  };

  hideUploadInfoDelayed = (files) => {
    this.setState({
      uploadingFiles: files,
      uploadInfoClosable: true
    }, () => {
      setTimeout(this.hideUploadInfo, 2000);
    });
  };

  hideUploadInfo = () => {
    if (!this.state.uploadInfoClosable) {
      return;
    }
    this.setState({
      uploadInfoVisible: false,
      uploadInfoClosable: false,
      uploadingFiles: [],
      synchronousUploadingFiles: []
    }, async () => {
      if (this.props.onRefresh) {
        this.props.onRefresh();
      }
    });
  };

  onUploadStatusChangedSynchronous = async () => {
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
    if (this.props.validate) {
      const validationResult = await this.props.validate(this.state.synchronousUploadingFiles);
      if (!validationResult) {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: [],
          synchronousUploadingFiles: []
        });
        return;
      }
    }
    this.setState({
      uploadInfoVisible: true
    });
    const uploadingFiles = this.state.synchronousUploadingFiles.map(f => ({
      uid: f.uid,
      name: f.name,
      status: 'waiting...',
      percent: 0
    }));
    const uploadFile = async (file) => {
      return new Promise((resolve) => {
        const files = this.state.uploadingFiles;
        const [uploadingFile] = files.filter(f => f.uid === file.uid);
        const updateError = (error) => {
          uploadingFile.percent = 100;
          uploadingFile.error = error;
          this.setState({uploadingFiles: files});
        };
        if (uploadingFile) {
          if (this.props.uploadToNFS && file.size >= MAX_NFS_FILE_SIZE_MB * MB) {
            updateError(`error: Maximum ${MAX_NFS_FILE_SIZE_MB}Gb per file`);
            resolve();
            return;
          }
          uploadingFile.status = 'uploading...';
          this.setState({
            uploadingFiles: files
          });
        }
        const updatePercent = ({loaded, total}) => {
          uploadingFile.percent = Math.min(100, Math.ceil(loaded / total * 100));
          this.setState({uploadingFiles: files});
        };
        const updateStatus = (status) => {
          uploadingFile.percent = 100;
          uploadingFile.status = status;
          this.setState({uploadingFiles: files});
        };
        const formData = new FormData();
        formData.append('file', file);
        const request = new XMLHttpRequest();
        request.withCredentials = true;
        request.upload.onprogress = function (event) {
          updatePercent(event);
        };
        request.upload.onload = function () {
          updateStatus('processing...');
        };
        request.upload.onerror = function () {
          updateError('error');
        };
        request.onreadystatechange = function () {
          if (request.readyState !== 4) return;

          if (request.status !== 200) {
            updateError(request.statusText);
          } else {
            try {
              const response = JSON.parse(request.responseText);
              if (response.status && response.status.toLowerCase() === 'error') {
                updateError(response.message);
              } else {
                updateStatus('done');
              }
            } catch (e) {
              updateError(`Error parsing response: ${e.toString()}`);
            }
          }
          resolve();
        };
        request.open('POST', this.props.action);
        request.send(formData);
      });
    };
    this.setState({uploadInfoVisible: true, uploadingFiles: uploadingFiles}, async () => {
      for (let i = 0; i < this.state.synchronousUploadingFiles.length; i++) {
        await uploadFile(this.state.synchronousUploadingFiles[i]);
      }
      this.hideUploadInfoDelayed(this.state.uploadingFiles);
    });
  };

  uploadItemToStorage = async (file) => {
    const request =
      new DataStorageGenerateUploadUrl(
        this.props.storageId,
        this.props.path ? `${this.props.path}/${file.name}` : file.name
      );
    await request.fetch();
    if (request.error) {
      return Promise.reject(new Error(request.error));
    } else {
      const url = request.value.url;
      const tagValue = request.value.tagValue;
      const cannedACLValue = request.value.cannedACLValue;
      return new Promise((resolve) => {
        const files = this.state.uploadingFiles;
        const [uploadingFile] = files.filter(f => f.uid === file.uid);

        const updatePercent = ({loaded, total}) => {
          uploadingFile.percent = Math.min(100, Math.ceil(loaded / total * 100));
          this.setState({uploadingFiles: files});
        };
        const updateError = (error) => {
          uploadingFile.percent = 100;
          uploadingFile.error = error;
          uploadingFile.done = true;
          this.setState({uploadingFiles: files});
        };
        const updateStatus = (status) => {
          uploadingFile.percent = 100;
          uploadingFile.status = status;
          if (status === 'canceled' || status === 'error' || status === 'done') {
            uploadingFile.done = true;
          }
          this.setState({uploadingFiles: files});
        };

        if (uploadingFile) {
          if (uploadingFile.done) {
            resolve();
            return;
          }
          if (file.size >= MAX_S3_FILE_SIZE_GB * GB) {
            updateError(`error: Maximum ${MAX_S3_FILE_SIZE_GB}Gb per file`);
            resolve();
            return;
          }
          uploadingFile.status = 'uploading...';
          this.setState({
            uploadingFiles: files
          });
        } else {
          resolve();
          return;
        }

        const request = new XMLHttpRequest();
        request.upload.onprogress = (event) => {
          updatePercent(event);
        };
        request.upload.onload = () => {
          updateStatus('processing...');
        };
        request.upload.onerror = () => {
          updateError('error');
        };
        request.upload.onabort = (event) => {
          updateStatus('canceled');
          resolve();
        };
        request.onreadystatechange = () => {
          if (request.readyState !== 4) return;

          if (request.status !== 200) {
            updateError(request.statusText);
          } else {
            updateStatus('done');
          }
          resolve();
        };
        request.open('PUT', url, true);
        if (tagValue) {
          request.setRequestHeader('x-amz-tagging', tagValue);
        }
        if (cannedACLValue) {
          request.setRequestHeader('x-amz-acl', cannedACLValue);
        }
        request.send(file);
        uploadingFile.abortCallback = () => {
          request.abort();
        };
        this.setState({
          uploadingFiles: files
        });
      });
    }
  };

  onUploadStatusChanged = (info) => {
    const files = this.state.uploadingFiles;
    let [uploadingFile] = files.filter(f => f.uid === info.file.uid);
    if (!uploadingFile) {
      uploadingFile = {
        name: info.file.name,
        uid: info.file.uid,
        percent: 0,
        error: undefined,
        status: 'Uploading...'
      };
      files.push(uploadingFile);
    }
    uploadingFile.error = info.file.error ? info.file.error.toString() : undefined;
    if (uploadingFile.error) {
      uploadingFile.percent = 100;
      uploadingFile.status = undefined;
    } else {
      if (info.event) {
        uploadingFile.percent = Math.ceil(info.event.percent);
      }
      if (uploadingFile.percent === 100) {
        uploadingFile.status = 'Processing...';
        if (info.file.response && info.file.response.status) {
          if (info.file.response.status.toLowerCase() === 'error') {
            uploadingFile.status = undefined;
            uploadingFile.error = info.file.response.status.message;
          } else if (info.file.response.status.toLowerCase() === 'ok') {
            uploadingFile.status = 'Done';
          }
        }
      }
    }
    if (info.fileList.filter(f => f.status === 'uploading').length === 0) {
      info.fileList.splice(0, info.fileList.length);
      this.hideUploadInfoDelayed(files);
    } else {
      this.showUploadInfo(files);
    }
  };

  uploadTimeout;

  startUploadTimeout = () => {
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
    this.uploadTimeout = setTimeout(this.onUploadStatusChangedSynchronous, 100);
  };

  startUploadToS3Timeout = () => {
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
    this.uploadTimeout = setTimeout(this.startUploadToS3, 100);
  };

  startUploadToS3 = async () => {
    if (this.props.validate) {
      const validationResult = await this.props.validate(this.state.synchronousUploadingFiles);
      if (!validationResult) {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: [],
          synchronousUploadingFiles: []
        });
        return;
      }
    }
    this.setState({
      uploadInfoVisible: true
    });
    const uploadingFiles = this.state.synchronousUploadingFiles.map(f => ({
      uid: f.uid,
      name: f.name,
      status: 'waiting...',
      percent: 0
    }));
    uploadingFiles.forEach(f => {
      f.abortCallback = () => {
        const files = this.state.uploadingFiles;
        const [file] = files.filter(uploadingFile => f.uid === uploadingFile.uid);
        if (file) {
          file.done = true;
          file.percent = 100;
          file.status = 'canceled';
          this.setState({uploadingFiles: files});
        }
      };
    });
    this.setState({uploadInfoVisible: true, uploadingFiles: uploadingFiles}, async () => {
      for (let i = 0; i < this.state.synchronousUploadingFiles.length; i++) {
        await this.uploadItemToStorage(this.state.synchronousUploadingFiles[i]);
      }
      this.hideUploadInfoDelayed(this.state.uploadingFiles);
    });
  };

  render () {
    const uploadProps = {
      multiple: this.props.multiple,
      showUploadList: false,
      action: this.props.action
    };
    if (this.props.uploadToS3) {
      uploadProps.beforeUpload = (file) => {
        const files = this.state.synchronousUploadingFiles;
        files.push(file);
        this.setState({
          synchronousUploadingFiles: files
        }, this.startUploadToS3Timeout);
        return false;
      };
    } else if (this.props.synchronous || this.props.uploadToNFS) {
      uploadProps.beforeUpload = (file) => {
        const files = this.state.synchronousUploadingFiles;
        files.push(file);
        this.setState({
          synchronousUploadingFiles: files
        }, this.startUploadTimeout);
        return false;
      };
    } else {
      uploadProps.onChange = this.onUploadStatusChanged;
    }
    const button = (
      <Button size="small" id="upload-button">
        <Icon type="upload" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
        <span style={{lineHeight: 'inherit', verticalAlign: 'middle'}}>{this.props.title}</span>
      </Button>
    );

    return (
      <div style={{display: 'inline'}}>
        <Upload {...uploadProps}>
          {
            this.props.uploadToS3 && !this.props.uploadToNFS &&
            <Tooltip title={`Maximum ${MAX_S3_FILE_SIZE_GB}Gb per file`} trigger="hover">
              {button}
            </Tooltip>
          }
          {
            this.props.uploadToNFS && !this.props.uploadToS3 &&
            <Tooltip title={`Maximum ${MAX_NFS_FILE_SIZE_MB}Mb per file`} trigger="hover">
              {button}
            </Tooltip>
          }
          {
            !this.props.uploadToS3 && !this.props.uploadToNFS && button
          }
        </Upload>
        <Modal
          footer={false}
          title="Uploading files..."
          closable={this.state.uploadInfoClosable}
          onCancel={this.hideUploadInfo}
          visible={this.state.uploadInfoVisible}>
          {
            this.state.uploadingFiles.map(f => {
              let status = 'active';
              if (f.error) {
                status = 'exception';
              } else if (f.percent === 100) {
                status = 'success';
              }
              let title = f.name;
              if (f.status) {
                title = `${f.name} - ${f.status}`;
              }
              return (
                <Row
                  style={{marginBottom: 20, marginTop: 0}}
                  type="flex"
                  key={f.uid}>
                  <Row type="flex" style={{width: '100%'}} >
                    <span>
                      {title}
                      {f.error && <span style={{color: 'red', fontSize: 'small'}}> - {f.error}</span>}
                    </span>
                  </Row>
                  <Row type="flex" style={{width: '100%'}}>
                    <Col span={22}>
                      <Progress
                        strokeWidth={3}
                        key={`${f.uid}-progress`}
                        percent={f.percent}
                        status={status} />
                    </Col>
                    {
                      this.props.uploadToS3 &&
                      !f.done &&
                      f.abortCallback &&
                      <Col span={2}>
                        <Row type="flex" justify="center">
                          <Button
                            shape="circle"
                            type="danger"
                            size="small"
                            icon="close"
                            onClick={() => f.abortCallback()} />
                        </Row>
                      </Col>
                    }
                  </Row>
                </Row>
              );
            })
          }
        </Modal>
      </div>
    );
  }
}
