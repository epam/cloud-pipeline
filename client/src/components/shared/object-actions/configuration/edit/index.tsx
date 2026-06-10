import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {ConfigurationEditModal} from './configuration-edit-modal.tsx';
import {EditOutlined} from '@ant-design/icons';

export {ConfigurationEditModal} from './configuration-edit-modal.tsx';
export type {ConfigurationEditModalProps} from './configuration-edit-modal.tsx';

export const ConfigurationEditButton = createActionButtonForModal(
  ConfigurationEditModal,
  <EditOutlined />,
  {
    size: 'small',
  },
);
