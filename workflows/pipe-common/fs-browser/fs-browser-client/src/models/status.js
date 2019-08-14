import {Remote} from './base';

export default class Status extends Remote {
  constructor(id) {
    super();
    this.url = `/status/${id}`;
  }
}
