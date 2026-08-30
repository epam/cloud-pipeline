import apiPost from '../base/api-post';

export default function stopRun(id, safe = false) {
  if (!id && safe) {
    return Promise.resolve({status: 'OK'});
  }
  return apiPost(
    `run/${id}/status`,
    {status: 'STOPPED'}
  )
}
