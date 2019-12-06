import Remote from './base';

export default class Download extends Remote {
  constructor(path) {
    super();
    this.url = `/download/${path.startsWith('/') ? path.substring(1) : path}`;
  }
}
