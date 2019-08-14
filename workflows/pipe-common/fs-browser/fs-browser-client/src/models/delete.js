import {RemotePost} from './base';

export default class Delete extends RemotePost {
  constructor(path) {
    super();
    this.url = `/delete/${path}`;
  }
}
