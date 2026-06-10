import {Pipeline} from '../../../../../@types/library.ts';
import roleModel from '../../../../../utils/roleModel';
import {PermissionsForm} from '../../../permissions-form';

type PipelinePermissionsTabProps = {
  pipeline: Pipeline;
};

function PipelinePermissionsTab({pipeline}: PipelinePermissionsTabProps) {
  return (
    <PermissionsForm
      readonly={pipeline.locked || !roleModel.writeAllowed(pipeline)}
      objectIdentifier={pipeline.id}
      objectType="pipeline"
      editOwnerAvailable={
        roleModel.isOwner(pipeline) || roleModel.isManager.pipelineAdmin({props: {}})
      }
    />
  );
}

export {PipelinePermissionsTab};
