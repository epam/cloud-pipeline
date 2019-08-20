import {Remote} from './base';

export default class UploadUrl extends Remote {
  constructor(path) {
    super(`UPLOAD_${path}`, {task: btoa(path), url: {url: 'upload url'}});
    this.url = `/uploadUrl/${path}`;
  }
}
