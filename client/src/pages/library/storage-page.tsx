import {useState} from 'react';
import {useParams} from 'react-router-dom';

import StorageBrowser from '../../components/pipelines/browser/data-storage';
import {GenerateUrlAction} from '../../components/library/library-actions/storage-actions/generate-url-action.tsx';
import {RefreshAction} from '../../components/library/library-actions/storage-actions/refresh-action.tsx';
import {SettingsAction} from '../../components/library/library-actions/storage-actions/settings-action.tsx';
import {SharedLinkAction} from '../../components/library/library-actions/storage-actions/shared-link-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function StoragePage() {
  const {id} = useParams<{id: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions, renderActionsAfterMenu} = actionsStore;

  const [showJobs, setShowJobs] = useState(false);
  const [showArchives, setShowArchives] = useState(false);
  const [showVersions, setShowVersions] = useState(false);

  useLibraryMenuActions(
    () => [
      'metadata',
      {
        key: 'jobs',
        type: 'toggle',
        title: 'Show import jobs',
        checked: showJobs,
        handler: setShowJobs,
      },
      {
        key: 'archive',
        type: 'toggle',
        title: 'Show archived files',
        checked: showArchives,
        handler: setShowArchives,
      },
      {
        key: 'version',
        type: 'toggle',
        title: 'Show file versions',
        checked: showVersions,
        handler: setShowVersions,
      },
    ],
    [showJobs, showArchives, showVersions],
  );

  return (
    <>
      <LegacyComponentBridge component={StorageBrowser} componentProps={{id}} />
      {renderActions(
        <GenerateUrlAction key="generate-url" storageId={id} />,
        <SharedLinkAction key="shared-link" storageId={id} />,
      )}
      {renderActionsAfterMenu(
        <SettingsAction key="settings" storageId={id} />,
        <RefreshAction key="refresh" storageId={id} />,
      )}
    </>
  );
}

export {StoragePage};
