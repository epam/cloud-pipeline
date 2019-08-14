import {Remote} from './base';

export default class List extends Remote {
  constructor(path) {
    super();
    this.url = `/view/${path}`;
  }
}
