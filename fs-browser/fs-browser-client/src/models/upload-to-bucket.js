import {action, observable} from 'mobx';
import Remote from './base';
import defer from './utilities/defer';

export default class UploadToBucket extends Remote {
  @observable percent = 0;

  constructor(id, url, tagValue, cannedACLValue, file) {
    super();
    this.url = url;
    this.file = file;
    this.name = file.name;
    this.tagValue = tagValue;
    this.cannedACLValue = cannedACLValue;
  }

  @action
  async fetch() {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
        try {
          await defer();
          const updatePercent = ({loaded, total}) => {
            this.percent = loaded / total;
          };
          const updateError = (error) => {
            this.percent = 1;
            this.error = error;
            this.failed = true;
            this._pending = false;
          };
          const updateStatus = (status) => {
            this.percent = 1;
            if (status === 'canceled' || status === 'error') {
              this.failed = true;
            }
            if (status === 'done') {
              this._loaded = true;
              this._pending = false;
            }
          };
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
          request.upload.onabort = () => {
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
          request.open('PUT', this.url, true);
          if (this.tagValue) {
            request.setRequestHeader('x-amz-tagging', this.tagValue);
          }
          if (this.cannedACLValue) {
            request.setRequestHeader('x-amz-acl', this.cannedACLValue);
          }
          request.send(this.file);
        } catch (e) {
          this.failed = true;
          this.error = e.toString();
        }
      });
    }
    return this._fetchPromise;
  }
}
