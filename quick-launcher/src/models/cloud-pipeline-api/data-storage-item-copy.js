import apiPost from '../base/api-post';

export default function copyDataStorageItem(
  storage,
  path,
  newPath,
  move = false
) {
  if (!path || !newPath) {
    return Promise.resolve();
  }
  return apiPost(
    `datastorage/${storage}/list`,
    [{
      action: move ? 'Move' : 'Copy',
      oldPath: path,
      path: newPath,
      type: path.endsWith('/') ? 'Folder' : 'File'
    }]
  );
}
