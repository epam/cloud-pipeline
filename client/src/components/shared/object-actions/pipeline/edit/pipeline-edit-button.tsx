import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {PipelineEditModal} from './pipeline-edit-modal.tsx';
import {EditOutlined} from '@ant-design/icons';

const PipelineEditButton = createActionButtonForModal(PipelineEditModal, <EditOutlined />, {});

export {PipelineEditButton};
