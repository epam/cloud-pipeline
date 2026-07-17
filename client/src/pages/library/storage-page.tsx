import {useCallback, useRef, useState} from 'react';
import {useParams, useSearchParams} from 'react-router-dom';

import StorageBrowser from '../../components/pipelines/browser/data-storage';
import {GenerateUrlAction} from '../../components/library/library-actions/storage-actions/generate-url-action.tsx';
import {RefreshAction} from '../../components/library/library-actions/storage-actions/refresh-action.tsx';
import {SettingsAction} from '../../components/library/library-actions/storage-actions/settings-action.tsx';
import {SharedLinkAction} from '../../components/library/library-actions/storage-actions/shared-link-action.tsx';
import type {LibraryMenuActions} from '../../components/library/library-actions/library-actions-store.ts';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type StoragePresentationOptions = {
  attributes: boolean;
  jobs: boolean;
  archives: boolean;
  versions: boolean;
};

const DEFAULT_PRESENTATION_OPTIONS: StoragePresentationOptions = {
  attributes: true,
  jobs: false,
  archives: false,
  versions: false,
};

function setSearchFlag(
  setSearchParams: ReturnType<typeof useSearchParams>[1],
  key: string,
  enabled: boolean,
) {
  setSearchParams(
    (prev) => {
      const next = new URLSearchParams(prev);
      if (enabled) {
        next.set(key, 'true');
      } else {
        next.delete(key);
      }
      return next;
    },
    {replace: true},
  );
}

function StoragePage() {
  const {id} = useParams<{id: string}>();
  const [searchParams, setSearchParams] = useSearchParams();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActions, renderActionsAfterMenu} = actionsStore;

  const refreshFnRef = useRef<((keepPage?: boolean) => void) | null>(null);

  const [showMetadata, setShowMetadata] = useState(false);
  const [showJobs, setShowJobs] = useState(false);
  const [presentationOptions, setPresentationOptions] = useState<StoragePresentationOptions>(
    DEFAULT_PRESENTATION_OPTIONS,
  );
  const showArchives = searchParams.get('archives') === 'true';
  const showVersions = searchParams.get('versions') === 'true';

  const setShowArchives = useCallback(
    (enabled: boolean) => setSearchFlag(setSearchParams, 'archives', enabled),
    [setSearchParams],
  );
  const setShowVersions = useCallback(
    (enabled: boolean) => setSearchFlag(setSearchParams, 'versions', enabled),
    [setSearchParams],
  );

  const onExposePresentationOptions = useCallback((options: StoragePresentationOptions) => {
    setPresentationOptions(options);
    if (!options.jobs) {
      setShowJobs(false);
    }
  }, []);

  useLibraryMenuActions(() => {
    const actions: LibraryMenuActions = [];
    if (presentationOptions.attributes) {
      actions.push({
        key: 'metadata',
        type: 'toggle',
        title: 'Show attributes',
        checked: showMetadata,
        handler: setShowMetadata,
      });
    }
    if (presentationOptions.jobs) {
      actions.push({
        key: 'jobs',
        type: 'toggle',
        title: 'Show import jobs',
        checked: showJobs,
        handler: setShowJobs,
      });
    }
    if (presentationOptions.archives) {
      actions.push({
        key: 'archive',
        type: 'toggle',
        title: 'Show archived files',
        checked: showArchives,
        handler: setShowArchives,
      });
    }
    if (presentationOptions.versions) {
      actions.push({
        key: 'version',
        type: 'toggle',
        title: 'Show file versions',
        checked: showVersions,
        handler: setShowVersions,
      });
    }
    return actions;
  }, [
    presentationOptions,
    showMetadata,
    showJobs,
    showArchives,
    showVersions,
    setShowArchives,
    setShowVersions,
  ]);

  return (
    <>
      <LegacyComponentBridge
        component={StorageBrowser}
        componentProps={{
          id,
          showMetadata,
          showJobs,
          onShowMetadataChange: setShowMetadata,
          onShowJobsChange: setShowJobs,
          onExposePresentationOptions,
          onExposeRefresh: (fn: (keepPage?: boolean) => void) => {
            refreshFnRef.current = fn;
          },
        }}
      />
      {renderActions(
        <GenerateUrlAction key="generate-url" storageId={id} />,
        <SharedLinkAction key="shared-link" storageId={id} />,
      )}
      {renderActionsAfterMenu(
        <SettingsAction key="settings" storageId={id} />,
        <RefreshAction key="refresh" storageId={id} onRefresh={() => refreshFnRef.current?.(true)} />,
      )}
    </>
  );
}

export {StoragePage};
