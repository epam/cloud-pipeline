import Remote from '../basic/Remote';

const RULES_URL = '/rules';

export default class NATRules extends Remote {
  constructor () {
    super();
    this.url = RULES_URL;
  }
}
