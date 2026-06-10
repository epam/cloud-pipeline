import {useState} from 'react';
import {useParams} from 'react-router-dom';

import Metadata from '../../components/pipelines/browser/Metadata';
import {SettingsAction} from '../../components/library/library-actions/metadata-actions/settings-action.tsx';
import {
  useLibraryLayoutOutletContext,
  useLibraryMenuActions,
} from '../layouts/library-layout-context.ts';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function MetadataPage() {
  const {id, class: metadataClass} = useParams<{id: string; class: string}>();
  const {actionsStore} = useLibraryLayoutOutletContext();
  const {renderActionsAfterMenu} = actionsStore;
  const [attributesVisible, setAttributesVisible] = useState(false);

  useLibraryMenuActions(
    () =>
      attributesVisible
        ? [
            {
              key: 'attributes',
              type: 'toggle',
              title: 'Attributes',
              checked: true,
              handler: setAttributesVisible,
            },
          ]
        : [],
    [attributesVisible],
  );

  return (
    <>
      <LegacyComponentBridge component={Metadata} componentProps={{id, class: metadataClass}} />
      {renderActionsAfterMenu(
        <SettingsAction
          key="settings"
          folderId={id}
          metadataClass={metadataClass}
          attributesVisible={attributesVisible}
          onToggleAttributes={setAttributesVisible}
        />,
      )}
    </>
  );
}

export {MetadataPage};
