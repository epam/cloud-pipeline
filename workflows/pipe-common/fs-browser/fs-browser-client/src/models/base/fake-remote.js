import Remote from './remote';
import defer from '../utilities/defer';

export default class FakeRemote extends Remote {
  constructor(localStorageKey, defaultObject) {
    super();
    this.localStorageKey = localStorageKey;
    if (localStorageKey && defaultObject && !localStorage.getItem(localStorageKey)) {
      localStorage.setItem(localStorageKey, JSON.stringify(defaultObject));
    }
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
          await FakeRemote.wait(1);
          const data = JSON.parse(this.localStorageKey ? localStorage.getItem(this.localStorageKey) : '{}');
          this.update({
            status: 'OK',
            payload: data,
          });
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
