import {Folder} from '../../../../@types/library.ts';
import {LibraryInlineActionsProps} from './types.ts';
import {IssuesButton} from './shared.tsx';
import {FolderEditButton} from '../../../shared/object-actions/folder/edit/folder-edit-button.tsx';
import {FolderRemoveButton} from '../../../shared/object-actions/folder/remove/folder-remove-button.tsx';

function FolderInlineActions(props: LibraryInlineActionsProps & {folder: Folder}) {
  const {item, onIssuesClick, folder} = props;
  return (
    <>
      {onIssuesClick && <IssuesButton item={item} onClick={onIssuesClick} />}
      <FolderEditButton folderId={folder.id} size="small" />
      <FolderRemoveButton folder={folder} size="small" />
    </>
  );
}

export {FolderInlineActions};
