import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {DeleteOutlined} from '@ant-design/icons';
import {DeletePipelineModal} from './delete-pipeline-modal.tsx';

const PipelineRemoveButton = createActionButtonForModal(DeletePipelineModal, <DeleteOutlined />, {
  danger: true,
});

export {PipelineRemoveButton};
