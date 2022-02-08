import RemotePost from '../basic/RemotePost';

const RULES_URL = '/rules';

export default class DeleteRules extends RemotePost {
  constructor () {
    super();
    this.constructor.fetchOptions = {
      headers: {
        'Content-type': 'application/json; charset=UTF-8'
      },
      mode: 'cors',
      credentials: 'include',
      method: 'DELETE'
    };
    this.url = RULES_URL;
  }
}
