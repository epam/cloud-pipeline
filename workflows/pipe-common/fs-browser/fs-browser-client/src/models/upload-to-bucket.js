import {observable} from 'mobx';
import {Remote} from './base';
import defer from './utilities/defer';

export default class UploadToBucket extends Remote {
  @observable percent = 0;

  constructor(id, url, file) {
    super();
    this.url = url;
    this.file = file;
    this.fetch();
  }

  static async wait(seconds) {
    return new Promise((resolve) => {
      setTimeout(resolve, seconds * 1000);
    });
  }

  async fetch() {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
        try {
          await defer();
          for (let i = 0; i < 60; i++) {
            this.percent = i / 60.0;
            await UploadToBucket.wait(1);
          }
          this.percent = 1.0;
          this._loaded = true;
          this.error = undefined;
          this.failed = false;
        } catch (e) {
          this.failed = true;
          this.error = e.toString();
        }

        this._pending = false;
        this._fetchPromise = null;
        resolve();
      });
    }
    return this._fetchPromise;
  }
}
