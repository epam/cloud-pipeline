import {deleteConfiguration, loadConfiguration} from '../../../../../api';
import {configurationKeys} from '../../../../../queries';

import {Configuration} from '../../../../../@types/library.ts';
import {createRemoveObjectModal} from '../../base/remove-object-modal/create-remove-object-modal.tsx';

const ConfigurationRemoveModal = createRemoveObjectModal({
  objectProp: 'configuration',
  loadFn: loadConfiguration,
  deleteFn: deleteConfiguration,
  queryKey: configurationKeys.detail,
  title: (configuration: Configuration) => (
    <span>
      Are you sure you want to remove configuration <b>{configuration.name}</b>?
    </span>
  ),
  canRemove: (user) =>
    user.admin || (user.roles ?? []).some((r) => r.name === 'ROLE_CONFIGURATION_MANAGER'),
});

export {ConfigurationRemoveModal};
