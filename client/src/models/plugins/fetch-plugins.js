import Remote from '../basic/Remote';

export default class FetchPlugins extends Remote {
  constructor () {
    super();
    this.url = '/plugins';
  };
}
