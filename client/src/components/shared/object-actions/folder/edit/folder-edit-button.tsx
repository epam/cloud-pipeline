import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {FolderEditModal} from './folder-edit-modal.tsx';
import {EditOutlined} from '@ant-design/icons';

const FolderEditButton = createActionButtonForModal(FolderEditModal, <EditOutlined />, {});

export {FolderEditButton};
