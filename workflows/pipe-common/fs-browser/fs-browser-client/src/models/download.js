import {Remote} from './base';

export default class Download extends Remote {
  constructor(path) {
    super();
    this.url = `/download/${path}`;
  }
}
