import Remote from '../basic/Remote';

export default class AWSRegionIds extends Remote {
  constructor (provider) {
    super();
    if (provider) {
      this.url = `/cloud/region/available?provider=${provider}`;
    } else {
      this.url = '/cloud/region/available';
    }
  };
}
