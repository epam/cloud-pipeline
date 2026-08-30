import apiDelete from '../base/api-delete';

export default function removeDataStorageItem(
  storage,
  path,
  totally = false
) {
  if (!path) {
    return Promise.resolve();
  }
  return apiDelete(
    `datastorage/${storage}/list?totally=${!!totally}`,
    [{
      path,
      type: path.endsWith('/') ? 'Folder' : 'File'
    }]
  );
}
