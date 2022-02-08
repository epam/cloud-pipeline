import RemotePost from '../basic/RemotePost';

const RULES_URL = '/rules';

export default class SetRules extends RemotePost {
  constructor () {
    super();
    this.url = RULES_URL;
  }
}
