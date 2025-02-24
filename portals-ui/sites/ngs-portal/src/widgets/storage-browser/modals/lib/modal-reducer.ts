import type { DataStorageItem } from '@cloud-pipeline/core';
import { StorageModal, UpdateEntityModalMode } from '../../constants';

type ModalState = {
  openModal: StorageModal | null;
  mode: UpdateEntityModalMode;
  item?: DataStorageItem;
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
      payload: { mode: UpdateEntityModalMode; item: DataStorageItem };
    }
  | {
      type: ModalActionType.OPEN_DELETE;
      payload: { item: DataStorageItem };
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
        item: action.payload.item,
      };
    case ModalActionType.OPEN_DELETE:
      return {
        ...state,
        openModal: StorageModal.Delete,
        item: action.payload.item,
      };
    case ModalActionType.CLOSE:
      return { ...state, openModal: null };
    case ModalActionType.RESET:
      return {
        openModal: null,
        mode: UpdateEntityModalMode.Create,
        item: undefined,
      };
    default:
      return state;
  }
};
