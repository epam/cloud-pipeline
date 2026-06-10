import {LibraryItemType} from '../../types.ts';
import {
  isConfiguration,
  isDataStorage,
  isFolder,
  isPipeline,
} from '../../../../utilities/guards.ts';
import {FolderInlineActions} from './folder-inline-actions.tsx';
import {StorageInlineActions} from './storage-inline-actions.tsx';
import {ConfigurationInlineActions} from './configuration-inline-actions.tsx';
import {PipelineInlineActions} from './pipeline-inline-actions.tsx';
import {LibraryInlineActionsProps} from './types.ts';

function LibraryItemInlineActions(props: LibraryInlineActionsProps) {
  const {item} = props;
  const {object} = item ?? {};
  if (isFolder(object) && [LibraryItemType.folder, LibraryItemType.project].includes(item.type)) {
    return <FolderInlineActions folder={object} {...props} />;
  }
  if (isDataStorage(object)) {
    return <StorageInlineActions storage={object} {...props} />;
  }
  if (isConfiguration(object)) {
    return <ConfigurationInlineActions configuration={object} {...props} />;
  }
  if (isPipeline(object)) {
    return <PipelineInlineActions pipeline={object} {...props} />;
  }
  return null;
}

export default LibraryItemInlineActions;
