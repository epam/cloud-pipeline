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
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import {
  Row,
  Upload,
  Button,
  Icon,
  message,
  Modal,
  Progress,
  Col,
  Tooltip
} from 'antd';
import S3Storage, {MAX_FILE_SIZE_DESCRIPTION} from '../../models/s3-upload/s3-storage';

const KB = 1024;
const MB = 1024 * KB;
const MAX_NFS_FILE_SIZE_MB = 500;

class UploadButton extends React.Component {
  static propTypes = {
    action: PropTypes.string,
    multiple: PropTypes.bool,
    onRefresh: PropTypes.func,
    synchronous: PropTypes.bool,
    uploadToS3: PropTypes.bool,
    uploadToNFS: PropTypes.bool,
    title: PropTypes.string,
    validate: PropTypes.func,
    path: PropTypes.string,
    storageInfo: PropTypes.object,
    region: PropTypes.string
  };

  state = {
    uploadInfoVisible: false,
    uploadInfoClosable: false,
    uploadingFiles: [],
    synchronousUploadingFiles: []
  };

  @observable s3Storage;
  @observable s3StorageError;

  componentDidMount () {
    this.createS3Storage();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.storageId !== prevProps.storageId ||
      this.props.uploadToS3 !== prevProps.uploadToS3 ||
      this.props.path !== prevProps.path ||
      this.props.storageInfo !== prevProps.storageInfo ||
      this.props.region !== prevProps.region
    ) {
      this.createS3Storage();
    }
  }

  createS3Storage = () => {
    const {storageId, uploadToS3, path: prefix, storageInfo, region} = this.props;
    if (uploadToS3 && storageId && storageInfo) {
      const {delimiter, path} = storageInfo;
      const storage = {
        id: storageId,
        path,
        delimiter,
        region
      };
      if (this.s3Storage) {
        this.s3Storage.storage = storage;
      } else {
        this.s3Storage = new S3Storage(storage);
      }
      if (this.s3Storage.prefix !== prefix) {
        this.s3Storage.prefix = prefix;
      }
      this.s3Storage.updateCredentials()
        .then(() => {
          this.s3StorageError = undefined;
        })
        .catch((e) => {
          this.s3Storage = undefined;
          this.s3StorageError = e.toString();
          message.error(e.toString(), 5);
        });
    } else {
      this.s3Storage = undefined;
    }
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

  hideUploadInfoDelayedIfDone = () => {
    const {uploadingFiles = []} = this.state;
    const allDone = !uploadingFiles.find(f => !f.done);
    const haveRetry = uploadingFiles.find(f => f.done && !!f.retryCb && (!!f.error || f.aborted));
    this.setState({
      uploadInfoClosable: allDone
    }, () => {
      if (allDone && !haveRetry) {
        setTimeout(this.hideUploadInfo, 2000);
      }
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
    this.setState({uploadInfoVisible: true, uploadingFiles: uploadingFiles}, async () => {
      for (let i = 0; i < this.state.synchronousUploadingFiles.length; i++) {
        await this.uploadItemToStorageSDK(this.state.synchronousUploadingFiles[i]);
      }
      this.hideUploadInfoDelayedIfDone();
    });
  };

  uploadItemToStorageSDK = (file) => {
    const files = this.state.uploadingFiles;
    const uploadingFile = files.find(f => f.uid === file.uid);
    if (uploadingFile) {
      uploadingFile.status = 'uploading...';
      this.setState({
        uploadingFiles: files
      });
    }

    const onProgress = (percent) => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile && !uFile.aborted) {
        uFile.percent = uFile.done
          ? 100
          : Math.min(100, Math.round(percent * 100));
        if (!uFile.done) {
          uFile.status = 'uploading...';
        }
        this.setState({uploadingFiles});
      }
    };

    const onError = (error) => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile && error) {
        uFile.error = error.toString();
        uFile.status = 'error';
        uFile.done = true;
        this.setState({uploadingFiles}, this.hideUploadInfoDelayedIfDone);
      }
    };

    const onDone = () => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.status = 'done';
        uFile.done = true;
        uFile.aborting = false;
        this.setState({uploadingFiles}, this.hideUploadInfoDelayedIfDone);
      }
    };

    const abort = () => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.aborted = true;
        uFile.aborting = false;
        uFile.status = 'aborted';
        uFile.uploadID = undefined;
        uFile.partNumber = undefined;
        uFile.parts = [];
        this.setState({uploadingFiles}, this.hideUploadInfoDelayedIfDone);
      }
    };

    const startAborting = () => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.uploadID = undefined;
        uFile.aborting = true;
        this.setState({uploadingFiles});
      }
    };

    const setAbort = (abortFn) => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.cancelCb = () => {
          startAborting();
          abortFn && abortFn().then(abort).catch(abort);
        };
        this.setState({uploadingFiles});
      }
    };

    const setMultipartUploadParts = (id, parts) => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.uploadID = id;
        uFile.parts = parts;
        this.setState({uploadingFiles});
      }
    };

    const onPartError = (partNumber, error) => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        uFile.error = error;
        uFile.partNumber = partNumber;
        this.setState({uploadingFiles});
      }
    };

    const callbacks = {
      onPartError,
      onProgress,
      setAbort,
      setMultipartUploadParts
    };

    const doUpload = (uploadID = undefined, partNumber = 0, multipartParts = []) => {
      return new Promise((resolve) => {
        this.s3Storage.doUpload(file, {uploadID, partNumber, multipartParts}, callbacks)
          .then((error) => {
            if (error) {
              onError(error);
              resolve();
            } else {
              onDone();
              resolve();
            }
          })
          .catch(error => {
            onError(error);
            resolve();
          });
      });
    };

    uploadingFile.retryCb = () => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile && this.s3Storage) {
        const {uploadID, partNumber, parts: multipartParts} = uFile;
        uFile.error = undefined;
        uFile.done = false;
        uFile.aborted = false;
        uFile.status = 'uploading...';
        uFile.percent = 0;
        this.setState({uploadingFiles}, () => {
          doUpload(uploadID, partNumber, multipartParts);
        });
      }
    };
    return doUpload();
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
      <Button
        size="small"
        id="upload-button"
        disabled={this.props.uploadToS3 && !!this.s3StorageError}
      >
        <Icon type="upload" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
        <span style={{lineHeight: 'inherit', verticalAlign: 'middle'}}>{this.props.title}</span>
      </Button>
    );

    return (
      <div style={{display: 'inline'}}>
        <Upload {...uploadProps} disabled={this.props.uploadToS3 && !!this.s3StorageError}>
          {
            this.props.uploadToS3 && this.s3StorageError && !this.props.uploadToNFS &&
            <Tooltip
              title={this.s3StorageError}
              trigger="hover"
            >
              {button}
            </Tooltip>
          }
          {
            this.props.uploadToS3 && !this.s3StorageError && !this.props.uploadToNFS &&
            <Tooltip title={`Maximum ${MAX_FILE_SIZE_DESCRIPTION} per file`} trigger="hover">
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
                    <Col span={21}>
                      <Progress
                        strokeWidth={3}
                        key={`${f.uid}-progress`}
                        percent={f.percent}
                        status={status} />
                    </Col>
                    {
                      this.props.uploadToS3 &&
                      <Col span={3}>
                        <Row type="flex" justify="space-around">
                          {
                            f.done && (f.error || f.aborted) && f.retryCb && !f.aborting &&
                            <Button
                              size="small"
                              shape="circle"
                              icon="reload"
                              onClick={() => f.retryCb()} />
                          }
                          {
                            !f.done && f.cancelCb && f.uploadID &&
                            <Button
                              size="small"
                              shape="circle"
                              type="danger"
                              icon="close"
                              onClick={() => f.cancelCb()} />
                          }
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

export default observer(UploadButton);
