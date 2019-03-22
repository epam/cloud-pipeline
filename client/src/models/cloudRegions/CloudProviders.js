import Remote from '../basic/Remote';

class CloudProviders extends Remote {
  constructor () {
    super();
    this.url = '/cloud/region/provider';
  };
}

export default new CloudProviders();
