import {deleteFolder, loadFolder} from '../../../../../api';
import {folderKeys} from '../../../../../queries';
import {createRemoveObjectModal} from '../../base/remove-object-modal/create-remove-object-modal.tsx';
import {Folder} from '../../../../../@types/library.ts';

const FolderRemoveModal = createRemoveObjectModal({
  loadFn: loadFolder,
  deleteFn: deleteFolder,
  queryKey: folderKeys.detail,
  objectProp: 'folder',
  title: (folder: Folder) => (
    <span>
      Are you sure you want to delete folder <b>{folder.name}</b>?
    </span>
  ),
});

export {FolderRemoveModal};
