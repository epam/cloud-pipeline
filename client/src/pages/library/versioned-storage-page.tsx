import {useState} from 'react';
import {useParams} from 'react-router-dom';

import VersionedStorage from '../../components/pipelines/browser/versioned-storage';
import {GenerateReportAction} from '../../components/library/library-actions/versioned-storage-actions/generate-report-action.tsx';
import {HistoryAction} from '../../components/library/library-actions/versioned-storage-actions/history-action.tsx';
import {RunAction} from '../../components/library/library-actions/versioned-storage-actions/run-action.tsx';
import {SettingsAction} from '../../components/library/library-actions/versioned-storage-actions/settings-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function VersionedStoragePage() {
  const {id} = useParams<{id: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions, renderActionsAfterMenu} = actionsStore;
  const [historyPanelOpen, setHistoryPanelOpen] = useState(false);

  useLibraryMenuActions(() => [], []);

  return (
    <>
      <LegacyComponentBridge component={VersionedStorage} componentProps={{id}} />
      {renderActions(
        <RunAction key="run" storageId={id} executable />,
        <GenerateReportAction key="generate-report" storageId={id} />,
        <HistoryAction
          key="history"
          storageId={id}
          historyPanelOpen={historyPanelOpen}
          onToggle={setHistoryPanelOpen}
        />,
      )}
      {renderActionsAfterMenu(<SettingsAction key="settings" storageId={id} />)}
    </>
  );
}

export {VersionedStoragePage};
