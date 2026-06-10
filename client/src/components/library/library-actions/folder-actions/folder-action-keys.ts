/** Menu key prefixes used by the create dropdown (mirrors Folder.jsx). */
export const CREATE_ACTION_KEYS = {
  pipeline: 'pipeline',
  storage: 'storage',
  versionedStorage: 'versioned',
  nfsStorage: 'nfs',
  configuration: 'configuration',
  folder: 'folder',
  omicsStore: 'omics',
} as const;

export const SETTINGS_ACTION_KEYS = {
  edit: 'edit',
  clone: 'clone',
  lock: 'lock',
  unlock: 'unlock',
  delete: 'delete',
} as const;
