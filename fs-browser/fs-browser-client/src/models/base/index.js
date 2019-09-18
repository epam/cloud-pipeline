import {observable, action, computed} from 'mobx';
import defer from '../utilities/defer';

// eslint-disable-next-line
let FS_BROWSER_API = process.env.FS_BROWSER_API;
if (FS_BROWSER_API !== '/' && FS_BROWSER_API.endsWith('/')) {
  FS_BROWSER_API = FS_BROWSER_API.substring(0, FS_BROWSER_API.length - 1);
}

function buildUrl(prefix, url) {
  if (!url) {
    return prefix;
  }
  if (!prefix || prefix.trim().length === 0) {
    // relative to client's root; we should remove leading '/' from url
    if (url.startsWith('/')) {
      return url.substring(1);
    }
    return url;
  }
  if (prefix === '/' && url.startsWith('/')) {
    return `${prefix}${url.substring(1)}`;
  }
  // some path specified
  if (!url.startsWith('/')) {
    return `${prefix}/${url}`;
  }
  return `${prefix}${url}`;
}

class Remote {
  static defaultValue = {};

  static fetchOptions = {
    mode: 'cors',
    credentials: 'include',
  };

  static prefix = FS_BROWSER_API || '';

  static auto = false;

  static isJson = true;

  url;

  @observable failed = false;

  @observable error = undefined;

  constructor() {
    if (this.constructor.auto) {
      this.fetch();
    }
  }

  @observable _pending = true;

  @computed
  get pending() {
    this._fetchIfNeeded();
    return this._pending;
  }

  @observable _loaded = false;

  @computed
  get loaded() {
    this._fetchIfNeeded();
    return this._loaded;
  }

  @observable _value = this.constructor.defaultValue;

  @computed
  get value() {
    this._fetchIfNeeded();
    return this._value;
  }

  @observable _response = undefined;

  @computed
  get response() {
    this._fetchIfNeeded();
    return this._response;
  }

  _loadRequired = !this.constructor.auto;

  async _fetchIfNeeded() {
    if (this._loadRequired) {
      this._loadRequired = false;
      await this.fetch();
    }
  }

  async fetchIfNeededOrWait() {
    if (this._loadRequired) {
      await this._fetchIfNeeded();
    } else if (this._fetchPromise) {
      await this._fetchPromise;
    }
  }

  invalidateCache() {
    this._loadRequired = true;
  }

  _fetchPromise = null;

  async fetch() {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
        const {prefix, fetchOptions} = this.constructor;
        try {
          await defer();
          let {headers} = fetchOptions;
          if (!headers) {
            headers = {};
          }
          fetchOptions.headers = headers;
          const response = await fetch(buildUrl(prefix, this.url), fetchOptions);
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          this.update(data);
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

  async silentFetch() {
    const {prefix, fetchOptions} = this.constructor;
    try {
      await defer();
      let {headers} = fetchOptions;
      if (!headers) {
        headers = {};
      }
      fetchOptions.headers = headers;
      const response = await fetch(`${prefix}${this.url}`, fetchOptions);
      const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
      this.update(data);
    } catch (e) {
      this.failed = true;
      this.error = e;
    }
  }

  // eslint-disable-next-line
  postprocess(value) {
    return value.payload;
  }

  @action
  update(value) {
    this._response = value;
    if (value.status && value.status === 401) {
      this.error = value.message;
      this.failed = true;
    } else if (value.status && value.status === 'OK') {
      this._value = this.postprocess(value);
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
    } else {
      this.error = value.message;
      this.failed = true;
      this._loaded = false;
    }
  }
}

export default Remote;
