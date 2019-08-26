import Remote from './base';

export default class Delete extends Remote {
  constructor(path) {
    super();
    this.url = `/delete/${path.startsWith('/') ? path.substring(1) : path}`;
  }
}
