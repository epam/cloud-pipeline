import request from './request';

function list() {
  return request('cluster/instance/allowed');
}

export {list};
