import {useParams} from 'react-router-dom';

import DetachedConfiguration from '../../components/pipelines/configuration/DetachedConfiguration';
import {ScheduleAction} from '../../components/library/library-actions/configuration-actions/schedule-action.tsx';
import {SettingsAction} from '../../components/library/library-actions/configuration-actions/settings-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function ConfigurationPage() {
  const {id} = useParams<{id: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActionsAfterMenu} = actionsStore;

  useLibraryMenuActions(() => [], []);

  return (
    <>
      <LegacyComponentBridge component={DetachedConfiguration} />
      {renderActionsAfterMenu(
        <ScheduleAction key="schedule" configurationId={id} />,
        <SettingsAction key="settings" configurationId={id} />,
      )}
    </>
  );
}

export {ConfigurationPage};
