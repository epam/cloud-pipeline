import {observable, makeObservable} from 'mobx';
import moment from 'moment-timezone';
import dataStorages from '../../models/dataStorage/DataStorages';
import DataStorageItemUpdate from '../../models/dataStorage/DataStorageItemUpdate';
import S3Storage from '../../models/s3-upload/s3-storage';
import whoAmI from '../../models/user/WhoAmI';

const KB = 1024;
const MB = 1024 * KB;
const MAX_NFS_FILE_SIZE_MB = 500;

async function uploadDefault (options) {
  const {
    file,
    fileName = file.name,
    url,
    progressCallback
  } = options;
  return new Promise((resolve, reject) => {
    let percent = 0;
    let indeterminate = false;
    const report = () => {
      if (progressCallback) {
        progressCallback({percent, indeterminate});
      }
    };
    const updatePercent = ({loaded, total}) => {
      indeterminate = false;
      percent = Math.min(100, Math.ceil(loaded / total * 100));
      report();
    };
    const formData = new FormData();
    formData.append('file', file, fileName);
    const request = new XMLHttpRequest();
    request.withCredentials = true;
    request.upload.onprogress = function (event) {
      updatePercent(event);
    };
    request.upload.onload = function () {
      indeterminate = true;
      report();
    };
    request.upload.onerror = function () {
      reject(new Error(`Error uploading ${file.name}`));
    };
    request.onreadystatechange = function () {
      if (request.readyState !== 4) return;

      if (request.status !== 200) {
        reject(new Error(`Error uploading ${file.name}: ${request.statusText}`));
      } else {
        try {
          const response = JSON.parse(request.responseText);
          if (response.status && response.status.toLowerCase() === 'error') {
            reject(new Error(`Error uploading ${file.name}: ${response.message}`));
            return;
          }
        } catch (e) {
          reject(new Error(`Error uploading ${file.name}: ${e.toString()}`));
          return;
        }
      }
      resolve();
    };
    request.open('POST', url);
    request.send(formData);
  });
}

class FileUpload {
  static generateUploadPath = (file, uploadRoot = '') => {
    const parts = file.name.split('.');
    const ext = parts.pop();
    const name = parts.join('.');
    const guid = moment.utc().format('YYYYMMDDHHmmssSSS');
    let root = uploadRoot;
    if (root.length > 0 && !root.endsWith('/')) {
      root = root.concat('/');
    }
    return `${root}${name}.${guid}.${ext}`;
  };

  error;
  progress; // 0..100
  indeterminate = false;
  done = false;
  fullStoragePath;
  cloudDataPath;
  resolvedPath;

  constructor (file, uploadStorageId, uploadPath) {
    makeObservable(this, {
      error: observable,
      progress: observable,
      indeterminate: observable,
      done: observable,
      fullStoragePath: observable,
      cloudDataPath: observable,
      resolvedPath: observable
    });
    this.file = file;
    this.uploadStorageId = uploadStorageId;
    this.uploadPath = uploadPath;
    this.listeners = [];
    this.abortController = new AbortController();
  }

  get name () {
    return this.file ? this.file.name : undefined;
  }

  get size () {
    return this.file ? this.file.size : undefined;
  }

  destroy = () => {
    this.listeners = [];
    this.file = undefined;
  };

  addEventListener = (listener) => {
    this.removeEventListener(listener);
    this.listeners.push(listener);
  };

  removeEventListener = (listener) => {
    this.listeners = this.listeners.filter((l) => l !== listener);
  }

  getState = () => ({
    error: this.error,
    progress: this.progress,
    indeterminate: this.indeterminate,
    done: this.done,
    fullStoragePath: this.fullStoragePath,
    cloudDataPath: this.cloudDataPath,
    resolvedPath: this.resolvedPath,
    storageId: this.uploadStorageId,
    storagePath: this.uploadPath,
    aborted: this.abortController.signal.aborted,
    file: this.file
  });

  report = () => {
    const state = this.getState();
    for (const listener of this.listeners) {
      listener(state);
    }
  };

  doUpload = async () => {
    try {
      this.report();
      this.progress = 0;
      await dataStorages.fetchIfNeededOrWait();
      const storage = (dataStorages.value || []).find((s) => s.id === this.uploadStorageId);
      if (!storage) {
        throw new Error('Storage not found');
      }
      const generatePath = (root) => {
        let result = root;
        if (result.length > 0 && !result.endsWith('/')) {
          result = result.concat('/');
        }
        return `${result}${this.uploadPath || ''}`;
      };
      this.fullStoragePath = generatePath(storage.pathMask);
      this.cloudDataPath = generatePath(storage.mountPoint || `/cloud-data/${storage.name}`);
      this.resolvedPath = /^nfs$/i.test(storage.type) ? this.cloudDataPath : this.fullStoragePath;
      const parentFolder = (this.uploadPath || '').split('/').slice(0, -1).join('/');
      const fileName = (this.uploadPath || '').split('/').pop();
      const storageWrapper = /^s3$/i.test(storage.type) ? new S3Storage(storage) : undefined;
      if (storageWrapper) {
        await whoAmI.fetchIfNeededOrWait();
        storageWrapper.prefix = parentFolder;
        await storageWrapper.doUpload(
          this.file,
          {
            fileName,
            partNumber: 0,
            owner: whoAmI.loaded && whoAmI.value && whoAmI.value.userName
              ? whoAmI.value.userName
              : undefined
          },
          {
            onProgress: (percent) => {
              this.progress = Math.max(0, Math.min(100, percent * 100));
              this.report();
            },
            setAbort: (onAbort) => {
              this.abortController.signal.addEventListener('abort', onAbort);
            }
          }
        );
      } else {
        if (this.file.size >= MAX_NFS_FILE_SIZE_MB * MB) {
          throw new Error(
            `file size too large (maximum ${MAX_NFS_FILE_SIZE_MB}Mb per file allowed)`
          );
        }
        const createFolder = async (folder) => {
          const parts = folder.split('/');
          const parentFolder = parts.slice(0, -1).join('/');
          if (parentFolder.length > 0) {
            await createFolder(parentFolder);
          }
          if (folder && folder.length > 0) {
            const request = new DataStorageItemUpdate(storage.id);
            const payload = [{
              path: folder,
              type: 'Folder',
              action: 'Create'
            }];
            await request.send(payload);
          }
        };
        // default upload
        await createFolder(parentFolder);
        const url = DataStorageItemUpdate.uploadUrl(storage.id, parentFolder);
        await uploadDefault({
          file: this.file,
          fileName,
          url,
          progressCallback: ({percent, indeterminate}) => {
            this.progress = Math.max(0, Math.min(100, percent * 100));
            this.indeterminate = indeterminate;
            this.report();
          }
        });
      }
    } catch (error) {
      this.error = error.message;
    } finally {
      this.done = true;
      this.report();
    }
  };

  abort = async () => {
    this.abortController.abort();
  };
}

export default FileUpload;
