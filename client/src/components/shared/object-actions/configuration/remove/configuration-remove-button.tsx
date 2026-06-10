import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {ConfigurationRemoveModal} from './configuration-remove-modal.tsx';
import {DeleteOutlined} from '@ant-design/icons';

const ConfigurationRemoveButton = createActionButtonForModal(
  ConfigurationRemoveModal,
  <DeleteOutlined />,
  {danger: true},
);

export {ConfigurationRemoveButton};
