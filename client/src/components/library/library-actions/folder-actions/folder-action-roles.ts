import {useMemo} from 'react';

import roleModel from '../../../../utils/roleModel.jsx';
import {useAuthenticatedUser} from '../../../../stores/users/hooks.ts';

export type FolderManagerRoles = {
  isPipelineManager: boolean;
  isPipelineAdmin: boolean;
  isStorageManager: boolean;
  isStorageAdmin: boolean;
  isFolderManager: boolean;
  isConfigurationManager: boolean;
  isVersionedStorageManager: boolean;
  isEntitiesManager: boolean;
};

/** Resolves global manager roles from the authenticated user (replaces roleModel.isManager.*(this)). */
export function useFolderManagerRoles(): FolderManagerRoles {
  const user = useAuthenticatedUser();
  return useMemo(
    () => ({
      isPipelineManager: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_PIPELINE_MANAGER),
      isPipelineAdmin: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_PIPELINE_ADMIN),
      isStorageManager: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_STORAGE_MANAGER),
      isStorageAdmin: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_STORAGE_ADMIN),
      isFolderManager: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_FOLDER_MANAGER),
      isConfigurationManager: !!roleModel.userHasRole(
        user,
        roleModel.ROLES.ROLE_CONFIGURATION_MANAGER,
      ),
      isVersionedStorageManager: !!roleModel.userHasRole(
        user,
        roleModel.ROLES.ROLE_VERSIONED_STORAGE_MANAGER,
      ),
      isEntitiesManager: !!roleModel.userHasRole(user, roleModel.ROLES.ROLE_ENTITIES_MANAGER),
    }),
    [user],
  );
}
