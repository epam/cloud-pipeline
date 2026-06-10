import {
  deleteDataStorageWithOption,
  loadDataStorage,
} from '../../../../../api/datastorage/datastorage-api.ts';
import {createRemoveObjectModal} from '../../base/remove-object-modal/create-remove-object-modal.tsx';
import {dataStorageKeys} from '../../../../../queries';

async function unregisterStorage(id: number) {
  await deleteDataStorageWithOption(id, false);
}

async function deleteStorage(id: number) {
  await deleteDataStorageWithOption(id, true);
}

const StorageRemoveModal = createRemoveObjectModal({
  objectProp: 'storage',
  queryKey: dataStorageKeys.detail,
  loadFn: loadDataStorage,
  deleteFn: deleteStorage,
  unregisterFn: unregisterStorage,
  canRemove: (user, st) => !st.sourceStorageId,
});

export {StorageRemoveModal};
