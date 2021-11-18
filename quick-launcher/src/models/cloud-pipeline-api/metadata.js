import apiPost from '../base/api-post';

export default function getMetadata(entities) {
  return apiPost('metadata/load', entities);
}
