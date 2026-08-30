import apiCall from '../base/api-call';

export default function downloadDataStorageItem (storage, path) {
  return apiCall(
    `datastorage/${storage}/download?path=${encodeURIComponent(path)}`,
    {},
    'GET',
    undefined,
    {isBlob: true}
  );
}
