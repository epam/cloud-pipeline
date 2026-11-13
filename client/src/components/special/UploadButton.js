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
  Modal,
  Progress,
  Col,
  Tooltip,
  Alert
} from 'antd';
import S3Storage, {MAX_FILE_SIZE_DESCRIPTION} from '../../models/s3-upload/s3-storage';
import DataStorageGenerateUploadUrl from '../../models/dataStorage/DataStorageGenerateUploadUrl';
import continuousFetch from '../../utils/continuous-fetch';
import {
  hasArchives,
  countArchives,
  extractionBlockedBy,
  uploadedArchivePaths,
  handleExtractArchives
} from '../pipelines/browser/data-storage/utils/extract-archives';

const KB = 1024;
const MB = 1024 * KB;
const MAX_NFS_FILE_SIZE_MB = 500;
// Shared by the confirmation and progress states, so a file's row is the same
// height before and after the upload starts.
const FILE_ROW_MARGIN_BOTTOM = 4;
// The same interval the Storage tasks table polls on.
const EXTRACTION_LIMIT_POLL_INTERVAL_MS = 5000;

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
    region: PropTypes.string,
    owner: PropTypes.string,
    onInitialized: PropTypes.func,
    style: PropTypes.object,
    disabled: PropTypes.bool,
    extractArchivesAvailable: PropTypes.bool,
    // The CurrentUserAsyncTasks store, which queues the extraction. Only the
    // storage page passes it; without it `extractionOffered` is false and the
    // confirmation pop-up is never shown.
    asyncTasks: PropTypes.object,
    // Opens the Storage tasks modal, whose state lives on the storage page.
    onShowDownloads: PropTypes.func
  };

  state = {
    uploadInfoVisible: false,
    uploadInfoClosable: false,
    uploadingFiles: [],
    synchronousUploadingFiles: [],
    pendingConfirmation: false,
    extractAfterUpload: false,
    // `checkActiveTasksLimit`'s answer, and whether the first one is still in
    // flight. Both buttons wait for it: clicking through before it lands could
    // queue an extraction the allowance has no room for.
    extractionLimitCheck: undefined,
    extractionCheckPending: false
  };

  @observable s3Storage;
  @observable s3StorageError;
  uploadButton;

  // Per path, not per batch: completion is reached several times for one batch,
  // and a retried archive landing later still has to go out. Reset in
  // `hideUploadInfo`.
  queuedExtractionPaths = new Set();

  unmounted = false;

  // A new selection bumps this, so an in-flight check for the previous batch
  // is ignored when it lands.
  extractionLimitToken = 0;

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
  }

  componentWillUnmount () {
    this.unmounted = true;
    this.stopExtractionLimitPolling();
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
  }

  triggerClick = () => {
    if (
      this.uploadButton &&
      this.uploadButton.refs &&
      this.uploadButton.refs.upload &&
      this.uploadButton.refs.upload.uploader &&
      this.uploadButton.refs.upload.uploader.fileInput &&
      this.uploadButton.refs.upload.uploader.fileInput.click
    ) {
      this.uploadButton.refs.upload.uploader.fileInput.click();
    }
  };

  createS3StorageForUpload = async (file) => {
    const {storageId, uploadToS3, path: prefix, storageInfo, region} = this.props;
    if (uploadToS3 && storageId && storageInfo) {
      const {delimiter, path} = storageInfo;
      const storage = {
        id: storageId,
        path,
        delimiter,
        region,
        read: false
      };
      let root = prefix || '';
      if (!root.endsWith('/')) {
        root = root.concat('/');
      }
      const storagePath = root.concat(file);
      const s3Storage = new S3Storage(storage, {storagePath, isFolder: false});
      s3Storage.prefix = prefix;
      return s3Storage;
    }
    return undefined;
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
      this.extractOrCloseDelayed();
    });
  };

  hideUploadInfoDelayedIfDone = () => {
    const {uploadingFiles = []} = this.state;
    const allDone = !uploadingFiles.find(f => !f.done);
    const haveRetry = uploadingFiles.find(f => f.done && !!f.retryCb && (!!f.error || f.aborted));
    this.setState({
      uploadInfoClosable: allDone
    }, () => {
      if (allDone) {
        // A retryable failure keeps the modal open, whether or not extraction
        // also runs.
        this.extractOrCloseDelayed(!!haveRetry);
      }
    });
  };

  /**
   * Reached by both completion paths once the batch is finished, and more than
   * once per batch. Arms the 2 s close timer, and on an "Upload and extract"
   * batch also queues whatever newly landed - without waiting on it.
   *
   * @param {boolean} [skipAutoClose=false] true while a file still offers a
   *   retry. Extraction is queued anyway.
   */
  extractOrCloseDelayed = (skipAutoClose = false) => {
    const {extractAfterUpload, uploadingFiles} = this.state;
    if (extractAfterUpload) {
      const paths = uploadedArchivePaths(uploadingFiles, this.props.path);
      const newPaths = paths.filter(path => !this.queuedExtractionPaths.has(path));
      if (newPaths.length > 0) {
        newPaths.forEach(path => this.queuedExtractionPaths.add(path));
        // Not awaited: `handleExtractArchives` owns the toast and the error.
        void handleExtractArchives(
          this.props.asyncTasks,
          this.props.storageId,
          newPaths,
          undefined,
          this.props.onShowDownloads
        );
      }
    }
    if (!skipAutoClose) {
      setTimeout(this.hideUploadInfo, 2000);
    }
  };

  hideUploadInfo = () => {
    if (!this.state.uploadInfoClosable) {
      return;
    }
    this.stopExtractionLimitPolling();
    const {
      uploadingFiles = []
    } = this.state;
    this.queuedExtractionPaths = new Set();
    this.setState({
      uploadInfoVisible: false,
      uploadInfoClosable: false,
      uploadingFiles: [],
      synchronousUploadingFiles: [],
      pendingConfirmation: false,
      extractAfterUpload: false,
      extractionLimitCheck: undefined,
      extractionCheckPending: false
    }, async () => {
      if (this.props.onRefresh) {
        this.props.onRefresh(uploadingFiles.map(f => f.name));
      }
    });
  };

  // Both halves are required. Without the store there is nothing to queue
  // with, and offering "Upload and extract" would upload the files and then
  // say nothing at all about the extraction that never happened.
  get extractionOffered () {
    const {extractArchivesAvailable, asyncTasks} = this.props;
    return !!extractArchivesAvailable &&
      !!asyncTasks &&
      typeof asyncTasks.startArchiveExtraction === 'function';
  }

  /**
   * Pauses a batch that holds an archive, where extraction is offered: the
   * modal opens in its confirmation state and waits for the choice.
   *
   * @param {function} startUpload `startUploadToS3` or
   *   `onUploadStatusChangedSynchronous`
   */
  onFilesSelected = (startUpload) => {
    const {synchronousUploadingFiles} = this.state;
    if (this.extractionOffered && hasArchives(synchronousUploadingFiles)) {
      this.setState({
        uploadInfoVisible: true,
        pendingConfirmation: true,
        uploadInfoClosable: true,
        uploadingFiles: [],
        extractionLimitCheck: undefined,
        extractionCheckPending: false
      }, this.startExtractionLimitPolling);
      return;
    }
    startUpload();
  };

  /**
   * The cap check `archiveDownloadAllowed` runs for a download, answered as an
   * alert in the pop-up rather than a toast. Optimistic while in flight and on
   * failure: the server enforces the quota itself.
   */
  // Polled so the user can wait in the open pop-up for a task to finish.
  // Stopped when the pop-up closes.
  startExtractionLimitPolling = () => {
    const {asyncTasks} = this.props;
    if (!asyncTasks || typeof asyncTasks.checkActiveTasksLimit !== 'function') {
      return;
    }
    this.stopExtractionLimitPolling();
    this.setState({extractionCheckPending: true});
    this.extractionLimitPoller = continuousFetch({
      call: this.checkExtractionLimit,
      intervalMS: EXTRACTION_LIMIT_POLL_INTERVAL_MS,
      identifier: 'upload-confirmation-archive-task-limit',
      onError: (error) => {
        console.warn('Could not check the active archive-task limit', error);
      }
    });
  };

  stopExtractionLimitPolling = () => {
    if (this.extractionLimitPoller) {
      this.extractionLimitPoller.stop();
      this.extractionLimitPoller = undefined;
    }
  };

  checkExtractionLimit = () => {
    const {asyncTasks} = this.props;
    if (!asyncTasks || typeof asyncTasks.checkActiveTasksLimit !== 'function') {
      return Promise.resolve();
    }
    this.extractionLimitToken += 1;
    const token = this.extractionLimitToken;
    return Promise.resolve()
      .then(() => asyncTasks.checkActiveTasksLimit())
      .then((check) => {
        if (this.unmounted || token !== this.extractionLimitToken) {
          return;
        }
        this.setState({extractionLimitCheck: check, extractionCheckPending: false});
      })
      .catch((error) => {
        if (!this.unmounted) {
          // Optimistic, as the download flow is: the server enforces the quota.
          this.setState({extractionCheckPending: false});
        }
        // Re-thrown so `continuousFetch` widens its interval rather than
        // re-polling a failing endpoint every 5 s - as the other pollers do.
        throw error;
      });
  };

  // Why "Upload and extract" is unavailable, shown in the pop-up's alert.
  get extractionDisabledReason () {
    return extractionBlockedBy(
      countArchives(this.state.synchronousUploadingFiles),
      this.state.extractionLimitCheck
    );
  }

  /**
   * Cancels a paused batch. Nothing uploaded, so no `onRefresh`.
   */
  cancelConfirmation = () => {
    this.stopExtractionLimitPolling();
    this.setState({
      uploadInfoVisible: false,
      uploadInfoClosable: false,
      pendingConfirmation: false,
      extractAfterUpload: false,
      extractionLimitCheck: undefined,
      extractionCheckPending: false,
      uploadingFiles: [],
      synchronousUploadingFiles: []
    });
  };

  /**
   * "Upload" and "Upload and extract" both land here, differing only in
   * `extractAfterUpload`.
   *
   * @param {boolean} extractAfterUpload
   */
  confirmUpload = (extractAfterUpload) => () => {
    this.stopExtractionLimitPolling();
    const startUpload = this.props.uploadToS3
      ? this.startUploadToS3
      : this.onUploadStatusChangedSynchronous;
    this.setState({
      pendingConfirmation: false,
      extractAfterUpload,
      // The confirmation state is dismissable; the progress state must not
      // inherit that, or dismissing it mid-upload clears `extractAfterUpload`
      // under an in-flight batch.
      uploadInfoClosable: false
    }, startUpload);
  };

  onUploadStatusChangedSynchronous = async () => {
    const {validate, validateAndFilter} = this.props;
    const {synchronousUploadingFiles} = this.state;
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
    if (validateAndFilter) {
      const validationResult = await validateAndFilter(synchronousUploadingFiles);
      if (validationResult && Array.isArray(validationResult) && validationResult.length) {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: validationResult,
          synchronousUploadingFiles: validationResult
        });
      } else {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: [],
          synchronousUploadingFiles: [],
          // Left set, these would arm extraction for a batch that never
          // uploaded.
          pendingConfirmation: false,
          extractAfterUpload: false,
          extractionLimitCheck: undefined,
          extractionCheckPending: false
        });
        return;
      }
    } else if (validate) {
      const validationResult = await validate(this.state.synchronousUploadingFiles);
      if (!validationResult) {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: [],
          synchronousUploadingFiles: [],
          pendingConfirmation: false,
          extractAfterUpload: false,
          extractionLimitCheck: undefined,
          extractionCheckPending: false
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
      size: f.size,
      status: 'waiting...',
      percent: 0
    }));
    const uploadFile = async (file) => {
      return new Promise(async (resolve) => {
        const files = this.state.uploadingFiles;
        const [uploadingFile] = files.filter(f => f.uid === file.uid);
        const updateError = (error) => {
          uploadingFile.percent = 100;
          uploadingFile.error = error;
          this.setState({uploadingFiles: files});
        };
        if (uploadingFile) {
          if (this.props.uploadToNFS && file.size >= MAX_NFS_FILE_SIZE_MB * MB) {
            updateError(`error: Maximum ${MAX_NFS_FILE_SIZE_MB}Mb per file`);
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
        if (this.props.uploadToAzure) {
          const basePath = this.props.path || '';
          const normalizedBase = basePath && !basePath.endsWith('/')
            ? `${basePath}/`
            : basePath;
          const targetPath = file.path || `${normalizedBase}${file.name}`;
          const urlRequest = new DataStorageGenerateUploadUrl(
            this.props.storageId,
            targetPath
          );
          await urlRequest.fetch();
          const url = urlRequest.value?.url;
          if (!url) {
            updateError('Failed to generate Azure upload URL');
            resolve();
            return;
          }
          request.open('PUT', url);
          request.setRequestHeader('x-ms-blob-type', 'BlockBlob');
          if (file.type) {
            request.setRequestHeader('Content-Type', file.type);
          }
          request.send(file);
        } else {
          request.open('POST', this.props.action);
          request.send(formData);
        }
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
        size: info.file.size,
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
    this.uploadTimeout = setTimeout(
      () => this.onFilesSelected(this.onUploadStatusChangedSynchronous),
      100
    );
  };

  startUploadToS3Timeout = () => {
    if (this.uploadTimeout) {
      clearTimeout(this.uploadTimeout);
      this.uploadTimeout = null;
    }
    this.uploadTimeout = setTimeout(
      () => this.onFilesSelected(this.startUploadToS3),
      100
    );
  };

  startUploadToS3 = async () => {
    if (this.props.validate) {
      const validationResult = await this.props.validate(this.state.synchronousUploadingFiles);
      if (!validationResult) {
        this.setState({
          uploadInfoVisible: false,
          uploadInfoClosable: false,
          uploadingFiles: [],
          synchronousUploadingFiles: [],
          // See `onUploadStatusChangedSynchronous`.
          pendingConfirmation: false,
          extractAfterUpload: false,
          extractionLimitCheck: undefined,
          extractionCheckPending: false
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
      size: f.size,
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

    let s3Storage;

    const doUpload = async (uploadID = undefined, partNumber = 0, multipartParts = []) => {
      try {
        if (!s3Storage) {
          s3Storage = await this.createS3StorageForUpload(file.name);
        }
        const uploadError = await s3Storage.doUpload(
          file,
          {
            uploadID,
            partNumber,
            multipartParts,
            owner: this.props.owner
          },
          callbacks
        );
        if (uploadError) {
          onError(uploadError);
        } else {
          onDone();
        }
      } catch (error) {
        onError(error);
      }
    };

    uploadingFile.retryCb = () => {
      const {uploadingFiles} = this.state;
      const uFile = uploadingFiles.find(f => f.uid === file.uid);
      if (uFile) {
        const {uploadID, partNumber, parts: multipartParts} = uFile;
        uFile.error = undefined;
        uFile.done = false;
        uFile.aborted = false;
        uFile.status = 'uploading...';
        uFile.percent = 0;
        this.setState({uploadingFiles}, () => {
          void doUpload(uploadID, partNumber, multipartParts);
        });
      }
    };
    return doUpload();
  };

  renderConfirmationFooter = () => {
    return (
      <Row type="flex" justify="end">
        <Button
          id="upload-cancel-button"
          style={{marginRight: 8}}
          onClick={this.cancelConfirmation}
        >
          Cancel
        </Button>
        {/* Only this one is disabled at the cap - plain Upload still works. */}
        <Button
          id="upload-and-extract-button"
          style={{marginRight: 8}}
          disabled={this.state.extractionCheckPending || !!this.extractionDisabledReason}
          onClick={this.confirmUpload(true)}
        >
          Upload and extract
        </Button>
        <Button
          id="upload-button-confirm"
          type="primary"
          disabled={this.state.extractionCheckPending}
          onClick={this.confirmUpload(false)}
        >
          Upload
        </Button>
      </Row>
    );
  };

  /**
   * One row renderer for both states, so the rows cannot drift apart in height.
   * The progress line is always rendered and only hidden by `visibility`, which
   * reserves its height. Its fixed 22px tracks `Progress`'s own.
   *
   * @param {object} file a `synchronousUploadingFiles` or `uploadingFiles` entry
   * @param {boolean} pending the batch only lists the selection so far
   */
  renderFileRow = (file, pending) => {
    const {uploadToS3} = this.props;
    let title = file.name;
    if (!pending && file.status) {
      title = `${file.name} - ${file.status}`;
    }
    // The span below is clipped to one line, so this attribute is the only
    // place a long name, its status and its error are shown in full.
    let hoverTitle = title;
    if (!pending && file.error) {
      hoverTitle = `${hoverTitle} - ${file.error}`;
    }
    let status = 'active';
    if (!pending) {
      if (file.error) {
        status = 'exception';
      } else if (file.percent === 100) {
        status = 'success';
      }
    }
    return (
      <Row
        style={{marginBottom: FILE_ROW_MARGIN_BOTTOM, marginTop: 0}}
        type="flex"
        key={file.uid}
      >
        <Row type="flex" style={{width: '100%'}}>
          <span
            title={hoverTitle}
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}
          >
            {title}
          </span>
        </Row>
        {
          // Outside the clipped span above, so a long reason wraps instead of
          // being cut off with the name.
          !pending && file.error && (
            <Row type="flex" style={{width: '100%'}}>
              <span className="cp-error" style={{fontSize: 'small'}}>
                - {file.error}
              </span>
            </Row>
          )
        }
        <Row
          type="flex"
          style={{
            width: '100%',
            marginTop: -4,
            height: 22,
            visibility: pending ? 'hidden' : 'visible'
          }}
        >
          <Col span={21}>
            <Progress
              strokeWidth={3}
              key={`${file.uid}-progress`}
              percent={pending ? 0 : file.percent}
              status={status} />
          </Col>
          {
            uploadToS3 && (
              <Col span={3}>
                <Row type="flex" justify="space-around">
                  {
                    !pending && file.done && (file.error || file.aborted) &&
                    file.retryCb && !file.aborting &&
                    <Button
                      size="small"
                      shape="circle"
                      icon="reload"
                      onClick={() => file.retryCb()} />
                  }
                  {
                    !pending && !file.done && file.cancelCb && file.uploadID &&
                    <Button
                      size="small"
                      shape="circle"
                      type="danger"
                      icon="close"
                      onClick={() => file.cancelCb()} />
                  }
                </Row>
              </Col>
            )
          }
        </Row>
      </Row>
    );
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
    } else if (
      this.props.synchronous ||
      this.props.uploadToAzure ||
      this.props.uploadToNFS
    ) {
      uploadProps.beforeUpload = (file) => {
        const files = this.state.synchronousUploadingFiles;
        files.push(file);
        this.setState({
          synchronousUploadingFiles: files
        }, this.startUploadTimeout);
        return false;
      };
    } else {
      // Not routed through `onFilesSelected`: antd's `Upload` owns the start
      // here, and no caller that enables `extractArchivesAvailable` uses this
      // path. Enabling the prop for such a storage does nothing today.
      uploadProps.onChange = this.onUploadStatusChanged;
    }
    const button = (
      <Button
        size="small"
        id="upload-button"
        disabled={this.props.disabled || (this.props.uploadToS3 && !!this.s3StorageError)}
      >
        <Icon type="upload" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
        <span style={{lineHeight: 'inherit', verticalAlign: 'middle'}}>{this.props.title}</span>
      </Button>
    );

    return (
      <div style={{display: 'inline'}}>
        <Upload
          {...uploadProps}
          disabled={this.props.disabled || (this.props.uploadToS3 && !!this.s3StorageError)}
          ref={component => {
            this.uploadButton = component;
          }}
          style={this.props.style}
        >
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
          footer={this.state.pendingConfirmation ? this.renderConfirmationFooter() : false}
          title={
            this.state.pendingConfirmation
              ? 'Upload files'
              : 'Uploading files...'
          }
          closable={this.state.pendingConfirmation ? true : this.state.uploadInfoClosable}
          onCancel={this.state.pendingConfirmation ? this.cancelConfirmation : this.hideUploadInfo}
          visible={this.state.uploadInfoVisible}>
          {
            // `info`, not `warning`: the selection is fine and Upload works.
            this.state.pendingConfirmation && this.extractionDisabledReason && (
              <Alert
                type="info"
                showIcon
                style={{marginBottom: 8}}
                message={this.extractionDisabledReason}
              />
            )
          }
          {
            this.state.pendingConfirmation &&
            this.state.synchronousUploadingFiles.map(
              file => this.renderFileRow(file, true)
            )
          }
          {
            !this.state.pendingConfirmation &&
            this.state.uploadingFiles.map(file => this.renderFileRow(file, false))
          }
        </Modal>
      </div>
    );
  }
}

export default observer(UploadButton);
