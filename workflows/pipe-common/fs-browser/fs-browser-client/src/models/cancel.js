import {Remote} from './base';

export default class Cancel extends Remote {
  constructor(id) {
    super();
    this.url = `/cancel/${id}`;
  }
}
