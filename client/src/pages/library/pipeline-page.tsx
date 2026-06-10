import {useParams} from 'react-router-dom';

import Pipeline from '../../components/pipelines/browser/Pipeline';
import {GitRepositoryAction} from '../../components/library/library-actions/pipeline-actions/git-repository-action.tsx';
import {SettingsAction as PipelineSettingsAction} from '../../components/library/library-actions/pipeline-actions/settings-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function PipelinePage() {
  const {id} = useParams<{id: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActionsAfterMenu} = actionsStore;

  useLibraryMenuActions(() => ['metadata', 'issues']);

  return (
    <>
      <LegacyComponentBridge component={Pipeline} componentProps={{id}} />
      {renderActionsAfterMenu(
        <PipelineSettingsAction key="settings" pipelineId={id} isOwner />,
        <GitRepositoryAction key="git" pipelineId={id} />,
      )}
    </>
  );
}

export {PipelinePage};
