import RemotePost from '../basic/RemotePost';

export default class ResolveIp extends RemotePost {
  constructor (hostname) {
    super();
    this.url = `/resolve?hostname=${hostname}`;
  }
}
