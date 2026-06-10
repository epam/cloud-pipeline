import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {EditOutlined} from '@ant-design/icons';
import {StorageEditModal} from './storage-edit-modal.tsx';

const StorageEditButton = createActionButtonForModal(StorageEditModal, <EditOutlined />, {
  size: 'small',
});

export {StorageEditButton};
