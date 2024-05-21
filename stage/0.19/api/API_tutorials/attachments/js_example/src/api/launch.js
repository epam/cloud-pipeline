import config from '../api/config';
import request from '../api/request';

function launch(options) {
  const payload = config.launchOptionsFn(options);
  return request('/run', 'POST', payload);
}

function getInfo(id) {
  return request(`/run/${id}`);
}

export {getInfo, launch};
