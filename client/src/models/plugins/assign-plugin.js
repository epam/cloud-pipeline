import RemotePost from '../basic/RemotePost';

export default class AssignPlugin extends RemotePost {
  constructor () {
    super();
    this.url = '/plugins/assign';
  };
}
