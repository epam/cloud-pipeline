import type {ComponentType} from 'react';
import {useLocation, useOutletContext} from 'react-router-dom';
import PipelineHistory from '../../components/pipelines/version/history/PipelineHistory';
import PipelineCode from '../../components/pipelines/version/code/PipelineCode';
import PipelineConfiguration from '../../components/pipelines/version/configuration/PipelineConfiguration';
import PipelineGraph from '../../components/pipelines/version/graph/PipelineGraph';
import PipelineDocuments from '../../components/pipelines/version/documents/PipelineDocuments';
import PipelineStorageRules from '../../components/pipelines/version/storageRules/PipelineStorageRules';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type PipelineVersionOutletContext = {
  onReloadTree?: (reloadRoot?: boolean, folderId?: number) => void;
  readOnly?: boolean;
};

const sectionComponents: Record<string, ComponentType<Record<string, unknown>>> = {
  history: PipelineHistory,
  code: PipelineCode,
  configuration: PipelineConfiguration,
  graph: PipelineGraph,
  workflow: PipelineGraph,
  documents: PipelineDocuments,
  storage: PipelineStorageRules,
};

function PipelineVersionSectionPage() {
  const {pathname} = useLocation();
  const outletContext = useOutletContext<PipelineVersionOutletContext>();
  const sectionKey = pathname.split('/').filter(Boolean)[2];
  const Component = sectionKey ? sectionComponents[sectionKey] : undefined;

  if (!Component) {
    return null;
  }

  return (
    <LegacyComponentBridge<PipelineVersionOutletContext>
      component={Component as never}
      componentProps={{
        onReloadTree: outletContext?.onReloadTree,
        readOnly: outletContext?.readOnly,
      }}
    />
  );
}

export {PipelineVersionSectionPage};
