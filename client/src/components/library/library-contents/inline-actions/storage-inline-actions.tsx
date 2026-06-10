import {DataStorage} from '../../../../@types/library.ts';
import {LibraryInlineActionsProps} from './types.ts';
import {StorageEditButton} from '../../../shared/object-actions/datastorage/edit/storage-edit-button.tsx';

function StorageInlineActions(props: LibraryInlineActionsProps & {storage: DataStorage}) {
  const canEditStorage = true;
  return <>{canEditStorage && <StorageEditButton storageId={props.storage.id} />}</>;
}

export {StorageInlineActions};
