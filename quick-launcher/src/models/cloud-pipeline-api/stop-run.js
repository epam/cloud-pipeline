import apiPost from '../base/api-post';

export default function stopRun(id) {
  return apiPost(
    `run/${id}/status`,
    {status: 'STOPPED'}
  )
}
