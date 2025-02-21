import type { DataStorageItemTypes } from '@cloud-pipeline/core';
import { StorageModal, UpdateEntityModalMode } from '../../constants';

type ModalState = {
  openModal: StorageModal | null;
  mode: UpdateEntityModalMode;
  entityType?: DataStorageItemTypes;
  entityName: string;
  pathToDelete: string;
};

export enum ModalActionType {
  OPEN_UPDATE = 'OPEN_UPDATE',
  OPEN_DELETE = 'OPEN_DELETE',
  CLOSE = 'CLOSE',
  RESET = 'RESET',
}

type ModalAction =
  | {
      type: ModalActionType.OPEN_UPDATE;
      payload: { mode: UpdateEntityModalMode; entityType: DataStorageItemTypes; entityName: string };
    }
  | {
      type: ModalActionType.OPEN_DELETE;
      payload: { entityType: DataStorageItemTypes; entityName: string; pathToDelete: string };
    }
  | { type: ModalActionType.CLOSE }
  | { type: ModalActionType.RESET };

export const modalReducer = (state: ModalState, action: ModalAction): ModalState => {
  switch (action.type) {
    case ModalActionType.OPEN_UPDATE:
      return {
        ...state,
        openModal: StorageModal.Update,
        mode: action.payload.mode,
        entityType: action.payload.entityType,
        entityName: action.payload.entityName,
      };
    case ModalActionType.OPEN_DELETE:
      return {
        ...state,
        openModal: StorageModal.Delete,
        entityType: action.payload.entityType,
        entityName: action.payload.entityName,
        pathToDelete: action.payload.pathToDelete,
      };
    case ModalActionType.CLOSE:
      return { ...state, openModal: null };
    case ModalActionType.RESET:
      return {
        openModal: null,
        mode: UpdateEntityModalMode.Create,
        entityType: undefined,
        entityName: '',
        pathToDelete: '',
      };
    default:
      return state;
  }
};
