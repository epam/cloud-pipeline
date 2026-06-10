import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {DeleteOutlined} from '@ant-design/icons';
import {StorageRemoveModal} from './storage-remove-modal.tsx';

const StorageRemoveButton = createActionButtonForModal(StorageRemoveModal, <DeleteOutlined />, {
  danger: true,
  size: 'small',
});

export {StorageRemoveButton};
