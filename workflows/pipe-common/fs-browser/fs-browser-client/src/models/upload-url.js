import {Remote} from './base';

export default class UploadUrl extends Remote {
  constructor(path) {
    super(`UPLOAD_${path}`, {task: btoa(path), url: {url: 'asd'}});
    this.url = `/uploadUrl/${path}`;
  }
}
