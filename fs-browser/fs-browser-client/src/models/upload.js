import Remote from './base';

export default class Upload extends Remote {
  constructor(id) {
    super();
    this.url = `/upload/${id}`;
  }
}
