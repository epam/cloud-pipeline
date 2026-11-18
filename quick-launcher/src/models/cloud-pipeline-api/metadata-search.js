import apiGet from '../base/api-get';

export default function searchMetadata(entityClass, key, value) {
  return apiGet(`metadata/search?entityClass=${entityClass}&key=${key}&value=${value}`);
}
