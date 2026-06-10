import {message} from 'antd';

import type {Folder} from '../../../../@types/library.ts';
import {TemplateDescription} from '../../../../@types/app.ts';

/** Mock handler — will be replaced with openCreatePipelineDialog from Folder.jsx. */
export function mockOpenCreatePipelineDialog(
  folderId: number,
  template?: TemplateDescription | null,
): void {
  message.info(
    `[mock] openCreatePipelineDialog(folder=${folderId}, template=${template?.id ?? 'default'})`,
  );
}

/** Mock handler — will be replaced with openCreateStorageDialog from Folder.jsx. */
export function mockOpenCreateStorageDialog(
  folderId: number,
  createNew: boolean,
  createNfs: boolean,
  createOmics: boolean,
): void {
  message.info(
    `[mock] openCreateStorageDialog(folder=${folderId}, new=${createNew}, nfs=${createNfs}, omics=${createOmics})`,
  );
}

/** Mock handler — will be replaced with openCreateVersionedStorageDialog from Folder.jsx. */
export function mockOpenCreateVersionedStorageDialog(folderId: number): void {
  message.info(`[mock] openCreateVersionedStorageDialog(folder=${folderId})`);
}

/** Mock handler — will be replaced with openAddFolderDialog from Folder.jsx. */
export function mockOpenAddFolderDialog(
  folderId: number,
  template?: TemplateDescription | null,
): void {
  message.info(
    `[mock] openAddFolderDialog(folder=${folderId}, template=${template?.id ?? 'none'})`,
  );
}

/** Mock handler — will be replaced with openCreateConfigurationDialog from Folder.jsx. */
export function mockOpenCreateConfigurationDialog(folderId: number): void {
  message.info(`[mock] openCreateConfigurationDialog(folder=${folderId})`);
}

/** Mock handler — will be replaced with openRenameFolderDialog from Folder.jsx. */
export function mockOpenRenameFolderDialog(folder: Folder): void {
  message.info(`[mock] openRenameFolderDialog(folder=${folder.id}, name=${folder.name})`);
}

/** Mock handler — will be replaced with openCloneFolderDialog from Folder.jsx. */
export function mockOpenCloneFolderDialog(folderId: number): void {
  message.info(`[mock] openCloneFolderDialog(folder=${folderId})`);
}

/** Mock handler — will be replaced with lockUnLockFolderConfirm from Folder.jsx. */
export function mockLockUnlockFolderConfirm(folder: Folder, lock: boolean): void {
  message.info(`[mock] lockUnLockFolderConfirm(folder=${folder.id}, lock=${lock})`);
}

/** Mock handler — will be replaced with deleteFolderConfirm from Folder.jsx. */
export function mockDeleteFolderConfirm(folder: Folder): void {
  message.info(`[mock] deleteFolderConfirm(folder=${folder.id}, name=${folder.name})`);
}
