import Remote from '../basic/Remote';
import {API_PATH, SERVER} from '../../config';

export default class GenerateDockerfile extends Remote {
  static url = (id, version, from) => {
    const el = document.createElement('div');
    let url = (SERVER || '') + (API_PATH + `/tool/${id}/dockerfile?version=${version}&from=${from}`).replace(/\/\//g, '/');
    el.innerHTML = '<a href="' + url + '"></a>';
    return el.firstChild.href;
  };

  constructor (id, version, from) {
    super();
    this.url = `/tool/${id}/dockerfile?version=${version}&from=${from}`;
  }
}
