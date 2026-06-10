import {useParams} from 'react-router-dom';

import PipelineDetails from '../../components/pipelines/version/PipelineDetails';
import {GitRepositoryAction} from '../../components/library/library-actions/pipeline-version-actions/git-repository-action.tsx';
import {RunAction} from '../../components/library/library-actions/pipeline-version-actions/run-action.tsx';
import {SettingsAction as PipelineVersionSettingsAction} from '../../components/library/library-actions/pipeline-version-actions/settings-action.tsx';
import {useLibraryLayoutOutletContext} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function PipelineVersionPage() {
  const {id, version} = useParams<{id: string; version: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions, renderActionsAfterMenu} = actionsStore;

  return (
    <>
      <LegacyComponentBridge component={PipelineDetails} />
      {renderActions(
        <RunAction
          key="run"
          pipelineId={id}
          version={version}
          configurations={['default', 'high-mem']}
          executable
        />,
      )}
      {renderActionsAfterMenu(
        <PipelineVersionSettingsAction key="settings" pipelineId={id} version={version} />,
        <GitRepositoryAction key="git" pipelineId={id} version={version} />,
      )}
    </>
  );
}

export {PipelineVersionPage};
