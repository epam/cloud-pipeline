export const NAME_VALIDATION_TEXT = "Name can contain only letters, digits, spaces, '_', '-', '@' and '.'.";

export const actionWords = {
  create: {
    success: 'created',
    error: 'create',
    pending: 'creating',
    action: 'Create',
  },
  update: {
    success: 'updated',
    error: 'update',
    pending: 'updating',
    action: 'Update',
  },
};

export enum UpdateEntityModalMode {
  Create = 'create',
  Update = 'update',
}

export enum StorageModal {
  Update = 'update',
  Delete = 'delete',
}
