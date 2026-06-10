import {useCallback, useMemo} from 'react';
import {LibraryContents} from '../../components/library/library-contents/library-contents.tsx';
import {useRoutedLibraryItem} from '../../components/library/model/hooks.ts';
import {
  getMetadataEntityRefFromLibraryItemId,
  LIBRARY_ROOT_ID,
  parseLibraryItemId,
} from '../../components/library/model/tree.ts';
import {LibraryItem, LibraryItemType} from '../../components/library/types.ts';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {useFolderShowDescriptions} from '../../stores/misc/folder-show-descriptions.ts';
import {CreateAction} from '../../components/library/library-actions/folder-actions/create-action.tsx';
import {SettingsAction} from '../../components/library/library-actions/folder-actions/settings-action.tsx';
import {UploadMetadataAction} from '../../components/library/library-actions/folder-actions/upload-metadata-action.tsx';

function getFolderFromLibraryItemId(itemId?: string): number | undefined {
  if (!itemId || itemId === LIBRARY_ROOT_ID) {
    return undefined;
  }
  const parsed = parseLibraryItemId(itemId);
  if (!parsed || parsed.type !== LibraryItemType.folder) {
    return undefined;
  }
  return parsed.identifier;
}

function LibraryContentsPage() {
  const [itemId, setItemId] = useRoutedLibraryItem();
  const folder = useMemo(() => getFolderFromLibraryItemId(itemId), [itemId]);
  const onItemClick = useCallback(
    (item: LibraryItem) => {
      console.log(item);
      setItemId(item.id);
    },
    [setItemId],
  );

  const [showDescriptions, setShowDescriptions] = useFolderShowDescriptions();

  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions, renderActionsAfterMenu, issuesPanel} = actionsStore;
  const {openEntity} = issuesPanel;
  const onItemIssuesClick = useCallback(
    (item: LibraryItem | undefined) => {
      const metadataEntityRef = item ? getMetadataEntityRefFromLibraryItemId(item.id) : undefined;
      openEntity(metadataEntityRef);
    },
    [openEntity],
  );

  const rootFolder = folder === undefined;

  useLibraryMenuActions(
    () =>
      rootFolder
        ? [
            {
              key: 'descriptions',
              type: 'toggle',
              title: 'Descriptions',
              checked: showDescriptions,
              handler: setShowDescriptions,
            },
          ]
        : [
            {
              key: 'descriptions',
              type: 'toggle',
              title: 'Descriptions',
              checked: showDescriptions,
              handler: setShowDescriptions,
            },
            'metadata',
            'issues',
          ],
    [showDescriptions, setShowDescriptions, rootFolder],
  );

  return (
    <>
      <LibraryContents
        className="w-full h-full overflow-auto"
        folder={folder}
        onItemClick={onItemClick}
        onItemIssuesClick={onItemIssuesClick}
        showDetails={showDescriptions}
        actions
      />
      {!rootFolder &&
        renderActions(
          <UploadMetadataAction key="upload-metadata" folderId={folder} />,
          <CreateAction key="create" folderId={folder} />,
        )}
      {!rootFolder && renderActionsAfterMenu(<SettingsAction key="settings" folderId={folder} />)}
    </>
  );
}

export {LibraryContentsPage};
