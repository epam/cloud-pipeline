/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject} from "mobx-react";
import {Row, Upload, Button, Icon, message, Modal, Progress, Col, Tooltip} from 'antd';

const KB = 1024;
const MB = 1024 * KB;
const GB = 1024 * MB;
const TB = 1024 * GB;
const MAX_FILE_SIZE_TB = 5;

const UPLOAD_CONCURRENCY_LIMIT = 9;
const S3_MIN_UPLOAD_CHUNK_SIZE = 5 * MB;
const S3_MAX_UPLOAD_CHUNKS_COUNT = 10000;
// Maximum 5Tb per file
const S3_MAX_FILE_SIZE = 5 * TB;

@inject('S3Storage')
export default class UploadButton extends React.Component {

  static propTypes = {
    action: PropTypes.string,
    multiple: PropTypes.bool,
    onRefresh: PropTypes.func,
    uploadToS3: PropTypes.bool,
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

  uploadItemToStorageSDK = async (file) => {
    const saveNotAbortedStorageObject = (item) => {
      const abortStorageObjects = localStorage.getItem('abortStorageObjects');
      let storageObjects = [];
      if (abortStorageObjects) {
        storageObjects = (JSON.parse(abortStorageObjects) || []);
        const [currentObject] = storageObjects.filter(object => object.uploadId === item.uploadId);
        if (!currentObject) {
          storageObjects.push(item);
        } else {
          currentObject.error = item.error;
        }
      } else {
        storageObjects.push(item);
      }
      localStorage.setItem('abortStorageObjects', JSON.stringify(storageObjects));
    };

    const removeNotAbortedStorageObject = (uploadId) => {
      const abortStorageObjects = JSON.parse(localStorage.getItem('abortStorageObjects'));
      const [current] = (abortStorageObjects || []).filter(obj => obj.uploadId === uploadId);
      if (current) {
        abortStorageObjects.splice(abortStorageObjects.indexOf(current), 1);
      }
      localStorage.setItem('abortStorageObjects', JSON.stringify(abortStorageObjects));
    };

    const saveNotCompletedStorageObject = (item) => {
      const completeStorageObjects = localStorage.getItem('completeStorageObjects');
      let storageObjects = [];
      if (completeStorageObjects) {
        storageObjects = (JSON.parse(completeStorageObjects) || []);
        const [currentObject] = storageObjects.filter(object => object.uploadId === item.uploadId);
        if (!currentObject) {
          storageObjects.push(item);
        } else {
          currentObject.parts = item.parts;
          currentObject.error = item.error;
        }
      } else {
        storageObjects.push(item);
      }
      localStorage.setItem('completeStorageObjects', JSON.stringify(storageObjects));
    };

    const removeNotCompletedStorageObject = (uploadId) => {
      const completeStorageObjects = JSON.parse(localStorage.getItem('completeStorageObjects'));
      const [current] = (completeStorageObjects || []).filter(obj => obj.uploadId === uploadId);
      if (current) {
        completeStorageObjects.splice(completeStorageObjects.indexOf(current), 1);
      }
      localStorage.setItem('completeStorageObjects', JSON.stringify(completeStorageObjects));
    };

    const saveCurrentUploadStorageObject = (item) => {
      let currentUpload = JSON.parse(localStorage.getItem('currentUpload'));
      if (currentUpload) {
        if (currentUpload.uploadId !== item.uploadId) {
          currentUpload = item;
        } else {
          if (item.parts) {
            currentUpload.parts = item.parts;
          }
          if (item.error) {
            if (currentUpload.errors) {
              currentUpload.errors.push(item.error);
            } else {
              currentUpload.errors = [item.error];
            }
          }
        }
      } else {
        currentUpload = item;
      }
      localStorage.setItem('currentUpload', JSON.stringify(currentUpload));
    };

    const removeCurrentUploadStorageObject = () => {
      localStorage.removeItem('currentUpload');
    };

    const fileName = file.name;
    const fileSize = file.size;
    const uploadChunkSize =
      Math.ceil(fileSize / S3_MIN_UPLOAD_CHUNK_SIZE) > S3_MAX_UPLOAD_CHUNKS_COUNT
        ? Math.ceil(fileSize / S3_MAX_UPLOAD_CHUNKS_COUNT)
        : S3_MIN_UPLOAD_CHUNK_SIZE;
    const parts = [];
    let partsCount = 0;
    let readPosition = 0;
    let uploadId = null;

    try {
      const data = await this.props.S3Storage.createMultipartUpload(fileName);
      uploadId = data.UploadId;
      saveCurrentUploadStorageObject({uploadId, fileSize, uploadChunkSize, fileName});
    } catch (e) {
      message.error(e.message, 7);
      return;
    }

    const files = this.state.uploadingFiles;
    const [uploadingFile] = files.filter(f => f.uid === file.uid);
    if (uploadingFile) {
      uploadingFile.status = 'uploading...';
      this.setState({
        uploadingFiles: files
      });
    }

    const updatePercent = (e, partsNum) => {
      if (uploadingFile.abortedByUser) {
        return;
      }
      const {loaded, total} = e;
      if (total === 0 || loaded === 0) return;
      const totalChunks = fileSize / uploadChunkSize;
      if (!uploadingFile.percentParts) {
        uploadingFile.percentParts = [];
      }
      const [currentPart] = uploadingFile.percentParts.filter(part => part.partsNum === partsNum);
      if (currentPart) {
        currentPart.percent = Math.min(100, Math.ceil(loaded / total * 100));
      } else {
        uploadingFile.percentParts.push({
          partsNum: partsNum,
          percent: Math.min(100, Math.ceil(loaded / total * 100))
        });
      }
      let sum = 0;
      const percents = uploadingFile.percentParts.map(part => part.percent);
      for (let i = 0; i < percents.length; i++) {
        sum += percents[i];
      }
      uploadingFile.percent = Math.min(100, Math.ceil(sum / totalChunks));
      this.setState({uploadingFiles: files});
    };

    const updateError = (error, retryCb = null, cancelCb = null) => {
      uploadingFile.percent = 100;
      uploadingFile.error = error;
      uploadingFile.retryCb = retryCb;
      uploadingFile.cancelCb = cancelCb;
      this.setState({uploadingFiles: files});
    };

    const updateStatus = (status) => {
      uploadingFile.percent = 100;
      uploadingFile.status = status;
      this.setState({uploadingFiles: files});
    };

    const tryAgain = async () => {
      uploadingFile.status = 'uploading...';
      uploadingFile.error = null;
      this.setState({uploadingFiles: files});
      await upload();
      if (!this.state.uploadingFiles
        .filter(f => f.status !== 'done' && f.status !== 'aborted').length) {
        this.hideUploadInfoDelayed(this.state.uploadingFiles);
      }
    };

    const abort = async () => {
      uploadingFile.status = 'aborting...';
      uploadingFile.error = null;
      this.setState({uploadingFiles: files});
      try {
        await this.props.S3Storage.abortMultipartUploadStorageObject(fileName, uploadId);
        removeNotAbortedStorageObject(uploadId);
        removeNotCompletedStorageObject(uploadId);
        updateStatus('aborted');
        updateError(null);
      } catch (e) {
        updateError('error aborting upload', abort);
        saveNotAbortedStorageObject({fileName, uploadId, error: e});
      }
      removeCurrentUploadStorageObject();
      if (!this.state.uploadingFiles
        .filter(f => f.status !== 'done' && f.status !== 'aborted').length) {
        this.hideUploadInfoDelayed(this.state.uploadingFiles);
      }
    };

    const complete = async () => {
      if (uploadingFile.abortedByUser) {
        return;
      }
      const sortedParts = parts.sort((a, b) => {
        if (a.PartNumber > b.PartNumber) {
          return 1;
        } else if (a.PartNumber < b.PartNumber) {
          return -1;
        }
        return 0;
      });
      try {
        await this.props.S3Storage.completeMultipartUploadStorageObject(
          fileName,
          sortedParts,
          uploadId
        );
        removeNotCompletedStorageObject(uploadId);
        updateStatus('done');
        updateError(null);
      } catch (e) {
        saveNotCompletedStorageObject({
          fileName,
          uploadId,
          parts: sortedParts,
          error: e
        });
        updateError('error completing file', complete, abort);
      }
      removeCurrentUploadStorageObject();
      if (!this.state.uploadingFiles
        .filter(f => f.status !== 'done' && f.status !== 'aborted').length) {
        this.hideUploadInfoDelayed(this.state.uploadingFiles);
      }
    };

    const upload = async () => {
      let promises = [];
      const currentAborts = [];
      partsCount = 0;
      readPosition = 0;

      if (fileSize) {
        if (fileSize >= S3_MAX_FILE_SIZE) {
          updateStatus('aborted');
          updateError('error: Maximum 5Tb per file');
          if (!this.state.uploadingFiles
            .filter(f => f.status !== 'done' && f.status !== 'aborted').length) {
            this.hideUploadInfoDelayed(this.state.uploadingFiles);
          }
          return;
        }
        uploadingFile.cancelCb = async () => {
          uploadingFile.abortedByUser = true;
          currentAborts.forEach(abort => abort && abort());
          await abort();
        };
        let requestCounter = 0;
        let results;
        do {
          const currentChunkSize = (readPosition + uploadChunkSize) > fileSize
            ? fileSize - readPosition
            : uploadChunkSize;
          const bulb = file.slice(readPosition, readPosition + currentChunkSize);
          partsCount += 1;
          readPosition += bulb.size;
          if (parts.map(part => part.PartNumber).indexOf(partsCount) === -1) {
            const partNumber = partsCount;
            const upload = this.props.S3Storage.multipartUploadStorageObject(
              fileName,
              bulb,
              partNumber,
              uploadId,
              (e) => updatePercent(e, partNumber)
            );
            currentAborts.push(upload.abort.bind(upload));
            promises.push(upload.promise().then((data) => {
              parts.push({
                ETag: data.ETag,
                PartNumber: partNumber
              });
              saveCurrentUploadStorageObject({uploadId, parts});
              return {data, error: null};
            }, (error) => {
              saveCurrentUploadStorageObject({uploadId, error});
              return {data: null, error: error.message};
            }));
          }
          requestCounter += 1;
          if (requestCounter === UPLOAD_CONCURRENCY_LIMIT) {
            results = await Promise.all(promises);
            promises = [];
            if (results.map(result => result.error).reduce((a, b) => b || a, 0)) {
              if (!uploadingFile.abortedByUser) {
                updateError('error uploading file', tryAgain, abort);
              }
            }
            requestCounter = 0;
          }
        } while (readPosition < fileSize && !uploadingFile.abortedByUser);

        if (promises.length) {
          results = await Promise.all(promises);
        }
        if (results.map(result => result.error).reduce((a, b) => b || a, 0)) {
          if (!uploadingFile.abortedByUser) {
            updateError('error uploading file', tryAgain, abort);
          }
        } else {
          await complete();
        }
      }
    };

    await upload();
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
        await this.uploadItemToStorageSDK(this.state.synchronousUploadingFiles[i]);
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
            this.props.uploadToS3
              ? (
                <Tooltip title={`Maximum ${MAX_FILE_SIZE_TB}Tb per file`} trigger="hover">
                  {button}
                </Tooltip>
              ) : button
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
                      this.props.uploadToS3 && (f.cancelCb || f.retryCb) && !f.done &&
                      <Col span={3}>
                        <Row type="flex" justify="space-around">
                          {
                            f.error && f.retryCb &&
                            <Button
                              size="small"
                              shape="circle"
                              icon="reload"
                              onClick={() => f.retryCb()} />
                          }
                          {
                            f.cancelCb &&
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
