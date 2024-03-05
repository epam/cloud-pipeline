import apiPost from '../base/api-post';

export default function updateDataStorageItem (storage, path, contents) {
  return apiPost(`datastorage/${storage}/content?path=${encodeURIComponent(path)}`, contents);
}
