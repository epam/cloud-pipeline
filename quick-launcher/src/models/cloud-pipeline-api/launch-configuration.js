import apiPost from '../base/api-post';

export default function launchConfiguration(id, configuration) {
  return apiPost(`runConfiguration`, {id, entries: [configuration]});
}
