import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {FolderRemoveModal} from './folder-remove-modal.tsx';
import {DeleteOutlined} from '@ant-design/icons';

const FolderRemoveButton = createActionButtonForModal(FolderRemoveModal, <DeleteOutlined />, {
  danger: true,
});

export {FolderRemoveButton};
