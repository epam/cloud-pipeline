import {RemotePost} from './base';

export default class Cancel extends RemotePost {
  constructor(id) {
    super();
    this.url = `/cancel/${id}`;
  }
}
