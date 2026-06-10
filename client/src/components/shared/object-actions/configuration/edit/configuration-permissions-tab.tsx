import {Configuration} from '../../../../../@types/library.ts';
import roleModel from '../../../../../utils/roleModel';
import {PermissionsForm} from '../../../permissions-form';

type ConfigurationPermissionsTabProps = {
  configuration: Configuration;
};

function ConfigurationPermissionsTab({configuration}: ConfigurationPermissionsTabProps) {
  const isReadOnly = configuration.locked || !roleModel.writeAllowed(configuration);
  return (
    <PermissionsForm
      readonly={isReadOnly}
      objectIdentifier={configuration.id}
      objectType="configuration"
    />
  );
}

export {ConfigurationPermissionsTab};
