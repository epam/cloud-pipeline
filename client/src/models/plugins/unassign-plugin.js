import RemotePost from '../basic/RemotePost';

export default class UnAssignPlugin extends RemotePost {
  constructor (id) {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8'
      },
      mode: 'cors',
      credentials: 'include',
      method: 'DELETE'
    };
    this.url = `/plugins/assign/${id}`;
  };
}
