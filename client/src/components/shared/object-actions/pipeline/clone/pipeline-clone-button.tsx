import {CopyOutlined} from '@ant-design/icons';
import {createActionButtonForModal} from '../../base/modal-button/modal-button-action.tsx';
import {PipelineCloneModal} from './pipeline-clone-modal.tsx';

const PipelineCloneButton = createActionButtonForModal(PipelineCloneModal, <CopyOutlined />, {});

export {PipelineCloneButton};
