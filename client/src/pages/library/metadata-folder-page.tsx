import {useParams} from 'react-router-dom';

import MetadataFolder from '../../components/pipelines/browser/MetadataFolder';
import {AddInstanceAction} from '../../components/library/library-actions/metadata-folder-actions/add-instance-action.tsx';
import {DeleteMetadataAction} from '../../components/library/library-actions/metadata-folder-actions/delete-metadata-action.tsx';
import {UploadMetadataAction} from '../../components/library/library-actions/metadata-folder-actions/upload-metadata-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function MetadataFolderPage() {
  const {id} = useParams<{id: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions} = actionsStore;

  useLibraryMenuActions(() => [], []);

  return (
    <>
      <LegacyComponentBridge component={MetadataFolder} componentProps={{id}} />
      {renderActions(
        <AddInstanceAction key="add-instance" folderId={id} />,
        <UploadMetadataAction key="upload-metadata" folderId={id} />,
        <DeleteMetadataAction key="delete-metadata" folderId={id} />,
      )}
    </>
  );
}

export {MetadataFolderPage};
