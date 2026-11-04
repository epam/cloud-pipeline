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
  Tooltip
} from 'antd';
import S3Storage, {MAX_FILE_SIZE_DESCRIPTION} from '../../models/s3-upload/s3-storage';
import DataStorageGenerateUploadUrl from '../../models/dataStorage/DataStorageGenerateUploadUrl';
import {
  hasArchives,
  uploadedArchivePaths,
  extractArchives,
  handleExtractArchives
} from './extract-storage-archives';

const KB = 1024;
const MB = 1024 * KB;
const MAX_NFS_FILE_SIZE_MB = 500;
// The one row margin both the confirmation state and the progress state use -
// so a file's row is as tall before the upload starts as after, and only the
// progress line's own visibility differs between the two. The exception is a
// file that failed: its reason renders on a line of its own, below, and that
// row does grow.
const FILE_ROW_MARGIN_BOTTOM = 4;

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
    // A seam for tests, which cannot reach the module's own `extractArchives`
    // without a module mock: a test passes a `fn()` here instead. Passed
    // through to `handleExtractArchives` as its own `extract` argument. Also
    // the place the real `RemotePost` model goes once `POST
    // /datastorage/{id}/extract` exists - no caller passes this today.
    extractArchives: PropTypes.func,
    // Opens the Downloads modal - passed straight through to
    // `handleExtractArchives`, which wires it to the link its queued toast
    // shows. The storage page is the only caller that can supply this: that
    // modal's state lives there, not here.
    onShowDownloads: PropTypes.func
  };

  state = {
    uploadInfoVisible: false,
    uploadInfoClosable: false,
    uploadingFiles: [],
    synchronousUploadingFiles: [],
    pendingConfirmation: false,
    extractAfterUpload: false
  };

  @observable s3Storage;
  @observable s3StorageError;
  uploadButton;

  // Guards each archive's path against being queued for extraction twice:
  // `hideUploadInfoDelayedIfDone` runs once per file on the S3 path (plus
  // once more after the upload loop awaits them all), and a retry can bring
  // the same batch back through here again once a previously-failed archive
  // lands. The guard has to work per path, not per batch - a single "has
  // extraction started" boolean would stop a retried archive from ever being
  // queued, since the same boolean would already read `true` from the
  // batch's first completion. An instance field, not state - it has no
  // bearing on what renders, only on which paths have already been queued.
  // Reset in `hideUploadInfo`, the only place a batch's state is fully
  // cleared.
  queuedExtractionPaths = new Set();

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
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
        // A retryable failure must keep the modal open regardless of
        // whether extraction also runs - `skipAutoClose` tells
        // `extractOrCloseDelayed` not to arm the close timer either way.
        // This same call also carries whatever archive has newly landed
        // since the last completion - a retry that just succeeded, say -
        // over to `extractOrCloseDelayed`, which queues it and decides on
        // the close timer from `skipAutoClose` alone: queuing a path is
        // never a reason, on its own, to skip or to arm that timer.
        this.extractOrCloseDelayed(!!haveRetry);
      }
    });
  };

  /**
   * The point both completion paths reach once every file in the batch is
   * finished - this is exactly where each used to arm the 2 s close timer
   * directly, and still does: the close timer is armed here whenever
   * `skipAutoClose` allows it, on every call this function gets for the
   * batch, including a later one a successful retry brings back through
   * here. A batch started through "Upload and extract" also queues the
   * extraction of whatever archive has newly landed since the last call -
   * but does not wait on it: extraction is an async task, exactly like an
   * archive download is, so the request only queues it and the modal closes
   * on the same timer an ordinary upload uses. The toast
   * `handleExtractArchives` shows is the whole of the user's feedback;
   * following the task itself happens in the downloads view, not here.
   * Queuing nothing new (nothing has landed since the last call, or there
   * was nothing to begin with) is never a reason to skip arming the close
   * timer - the two are decided independently.
   *
   * @param {boolean} [skipAutoClose=false] true when a file in the batch
   *   still offers a retry (the S3 path's own affordance). The modal must
   *   not auto-close while a retry is still possible, so the close timer is
   *   not armed in that case - extraction, if any, is still queued over
   *   whatever already landed.
   */
  extractOrCloseDelayed = (skipAutoClose = false) => {
    const {extractAfterUpload, uploadingFiles} = this.state;
    if (extractAfterUpload) {
      const paths = uploadedArchivePaths(uploadingFiles, this.props.path);
      // Per-path guard, not a batch-wide one: this function can be reached
      // more than once for the same batch (the S3 path calls it once per
      // file, plus once after the loop, plus once more if a retry succeeds
      // later), and a path already queued must not be sent again - but a
      // path that only lands on a later call (a successful retry) still has
      // to go out, which a single "has extraction started" boolean would
      // have missed entirely.
      const newPaths = paths.filter(path => !this.queuedExtractionPaths.has(path));
      if (newPaths.length > 0) {
        newPaths.forEach(path => this.queuedExtractionPaths.add(path));
        // `this.props.extractArchives` is the test seam described on the
        // propType above; no caller sets it today, so this falls through to
        // the module's own mock in every real render.
        const extract = this.props.extractArchives || extractArchives;
        // Fired and left to run: `handleExtractArchives` owns the toast and
        // its own error handling, and the modal does not sit open waiting
        // for it. `this.props.onShowDownloads` is threaded straight through -
        // it is what turns the toast's "Show storage tasks" into a link.
        void handleExtractArchives(
          this.props.storageId,
          newPaths,
          undefined,
          extract,
          this.props.onShowDownloads
        );
      }
      // Every archive failed or was aborted, or nothing new has landed since
      // the last call - either way there is nothing to queue this time, but
      // that says nothing about the close timer below, which is armed from
      // `skipAutoClose` alone.
    }
    if (!skipAutoClose) {
      setTimeout(this.hideUploadInfo, 2000);
    }
  };

  hideUploadInfo = () => {
    if (!this.state.uploadInfoClosable) {
      return;
    }
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
      extractAfterUpload: false
    }, async () => {
      if (this.props.onRefresh) {
        this.props.onRefresh(uploadingFiles.map(f => f.name));
      }
    });
  };

  /**
   * The decision point a selected batch goes through once the 100 ms
   * debounce settles, before anything uploads. When the caller has enabled
   * `extractArchivesAvailable` and the batch holds at least one archive, the
   * upload is paused: the modal opens in its confirmation state and
   * `startUpload` is not called until the user picks "Upload" or "Upload and
   * extract". Otherwise `startUpload` runs immediately, exactly as before
   * this mode existed.
   *
   * @param {function} startUpload the handler the caller would have gone to
   *   directly - `startUploadToS3` or `onUploadStatusChangedSynchronous`.
   */
  onFilesSelected = (startUpload) => {
    const {extractArchivesAvailable} = this.props;
    const {synchronousUploadingFiles} = this.state;
    if (extractArchivesAvailable && hasArchives(synchronousUploadingFiles)) {
      this.setState({
        uploadInfoVisible: true,
        pendingConfirmation: true,
        uploadInfoClosable: true,
        uploadingFiles: []
      });
      return;
    }
    startUpload();
  };

  /**
   * Cancels a paused batch: nothing was uploaded, so `onRefresh` is not
   * called, and every bit of selection state is cleared so the next pick
   * starts clean.
   */
  cancelConfirmation = () => {
    this.setState({
      uploadInfoVisible: false,
      uploadInfoClosable: false,
      pendingConfirmation: false,
      extractAfterUpload: false,
      uploadingFiles: [],
      synchronousUploadingFiles: []
    });
  };

  /**
   * "Upload" and "Upload and extract" both land here, only disagreeing on
   * `extractAfterUpload`. Starts through the same handler the batch would
   * have used had the confirmation step never shown.
   *
   * @param {boolean} extractAfterUpload
   */
  confirmUpload = (extractAfterUpload) => () => {
    const startUpload = this.props.uploadToS3
      ? this.startUploadToS3
      : this.onUploadStatusChangedSynchronous;
    this.setState({
      pendingConfirmation: false,
      extractAfterUpload,
      // The confirmation state leaves `uploadInfoClosable: true` behind (the
      // modal is dismissable while it only lists the selection). The
      // progress state that follows must not inherit that: before this
      // confirmation step existed, the initial `false` was never touched
      // until a batch actually finished, so it is reset here to restore
      // that - without it the close X rendered mid-upload, and dismissing it
      // ran `hideUploadInfo`, which cleared `extractAfterUpload` under an
      // in-flight batch.
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
          // `pendingConfirmation` and `extractAfterUpload` are independent
          // of what `validateAndFilter` decided; left set, they would
          // survive with the modal hidden until some later `hideUploadInfo`
          // clears them, and arm extraction for a batch that never
          // actually uploaded.
          pendingConfirmation: false,
          extractAfterUpload: false
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
          extractAfterUpload: false
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
          // See the matching comment in `onUploadStatusChangedSynchronous`:
          // these two are independent of `validate`'s own outcome.
          pendingConfirmation: false,
          extractAfterUpload: false
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
        <Button
          id="upload-and-extract-button"
          style={{marginRight: 8}}
          onClick={this.confirmUpload(true)}
        >
          Upload and extract
        </Button>
        <Button
          id="upload-button-confirm"
          type="primary"
          onClick={this.confirmUpload(false)}
        >
          Upload
        </Button>
      </Row>
    );
  };

  /**
   * One row renderer for both the confirmation state and the progress state,
   * so a file's row is built the same way in either state and the two
   * cannot drift apart in height - which is how they used to: the
   * confirmation state rendered a single line while the progress state
   * rendered a name line plus a `<Progress>` line, at a different margin.
   *
   * Both states now render the same two lines. The progress line - the
   * `<Progress>` itself, in its `Col span={21}`, and on an S3 upload the
   * retry/cancel actions in their `Col span={3}` - is always present, so its
   * height is always reserved; only `visibility` hides it while `pending` is
   * true and nothing has started uploading yet. The line itself is a fixed
   * 22px tall (with a -4px top margin), chosen to keep the rows compact
   * rather than guessed from nothing - the real `<Progress>` is still
   * rendered inside it so the two states stay identical in every other
   * respect. That 22px is tied to `Progress`'s own rendered height at this
   * `strokeWidth`, and needs revisiting if that height ever changes.
   *
   * @param {object} file a `synchronousUploadingFiles` entry (`uid`, `name`,
   *   `size`) while `pending`, or a `uploadingFiles` entry (also carrying
   *   `status`, `percent`, `error`, `done`, `aborted`, `aborting`,
   *   `retryCb`, `cancelCb`, `uploadID`) once the upload has started.
   * @param {boolean} pending true while the batch only lists the selection,
   *   nothing uploading yet.
   */
  renderFileRow = (file, pending) => {
    const {uploadToS3} = this.props;
    let title = file.name;
    if (!pending && file.status) {
      title = `${file.name} - ${file.status}`;
    }
    // The hover title carries the status and the error too: the span below
    // is clipped to one line, so the attribute is the only place a long
    // name, a status or a failure reason is ever shown in full together.
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
          // Outside the clipped span above, and with no `nowrap` of its own,
          // so a long failure reason wraps onto as many lines as it needs
          // instead of being cut off together with the name.
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
      // Not routed through `onFilesSelected`: `antd`'s own `Upload` owns the
      // start here (a GCP storage posts as soon as a file is picked), and no
      // caller that enables `extractArchivesAvailable` uses this path. If
      // that ever changes, the pause has to be built here too - enabling the
      // prop for such a storage today does nothing, the upload just starts.
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
