import {observable, action, computed} from 'mobx';
import defer from '../utilities/defer';

class RemotePost {
  static fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;',
    },
  };

  static prefix = '';

  static isJson = true;

  static noResponse = false;

  url;

  @observable _pending = false;

  @computed
  get pending() {
    return this._pending;
  }

  @observable _loaded = false;

  @computed
  get loaded() {
    return this._loaded;
  }

  @observable _response = undefined;

  @computed
  get response() {
    return this._response;
  }

  async fetch() {
    await this.send({});
  }

  _fetchIsExecuting = false;

  async send(body) {
    if (!this._postIsExecuting) {
      this._pending = true;
      this._postIsExecuting = true;
      const {prefix, fetchOptions} = this.constructor;
      try {
        await defer();
        let {headers} = fetchOptions;
        if (!headers) {
          headers = {};
        }
        fetchOptions.headers = headers;
        let stringifiedBody;
        try {
          stringifiedBody = JSON.stringify(body);
        } catch (___) {}
        const response = await fetch(
          `${prefix}${this.url}`,
          {...fetchOptions, body: stringifiedBody},
        );
        if (!this.constructor.noResponse) {
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          this.update(data);
        } else {
          this.update({status: 'OK', payload: {}});
        }
      } catch (e) {
        this.failed = true;
        this.error = e.toString();
      } finally {
        this._postIsExecuting = false;
      }

      this._pending = false;
      this._fetchIsExecuting = false;
    }
  }

  postprocess(value) {
    return value.payload;
  }

  @action
  update(value) {
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
    this._response = value;
  }

  get value() {
    return this._value;
  }
}

export default RemotePost;
