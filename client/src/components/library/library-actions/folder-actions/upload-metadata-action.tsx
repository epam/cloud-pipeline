import {useCallback} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import UploadButton from '../../../special/UploadButton.jsx';
import MetadataEntityUpload from '../../../../models/folderMetadata/MetadataEntityUpload.js';
import {folderKeys, folderQueryOptions, libraryTreeKeys} from '../../../../queries';
import roleModel from '../../../../utils/roleModel.jsx';
import {useFolderManagerRoles} from './folder-action-roles.ts';

type UploadMetadataActionProps = CommonProps & {
  folderId: number;
  readOnly?: boolean;
  onUploaded?: () => void;
};

function UploadMetadataAction(props: UploadMetadataActionProps) {
  const {folderId, readOnly = false, onUploaded} = props;
  const queryClient = useQueryClient();
  const {data: folder} = useQuery(folderQueryOptions(folderId));
  const roles = useFolderManagerRoles();

  const onRefresh = useCallback(async () => {
    await queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)});
    await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
    onUploaded?.();
  }, [folderId, onUploaded, queryClient]);

  // Folder.jsx: roleModel.writeAllowed(folder) && !readOnly && folderId !== undefined
  //             && !listingMode && roleModel.isManager.entities
  if (!folder || readOnly || !roleModel.writeAllowed(folder) || !roles.isEntitiesManager) {
    return null;
  }

  return (
    <UploadButton
      multiple={false}
      synchronous
      onRefresh={onRefresh}
      title="Upload metadata"
      action={MetadataEntityUpload.uploadUrl(folderId)}
    />
  );
}

export {UploadMetadataAction};
