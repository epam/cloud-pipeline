import Remote from './base';

export default class Download extends Remote {
  constructor(path) {
    super(`DOWNLOAD_${path}`, {task: btoa(path)});
    this.url = `/download/${path}`;
  }
}
