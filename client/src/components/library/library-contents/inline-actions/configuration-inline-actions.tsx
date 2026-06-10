import {Configuration} from '../../../../@types/library.ts';
import {LibraryInlineActionsProps} from './types.ts';
import {ConfigurationEditButton} from '../../../shared/object-actions/configuration/edit';

function ConfigurationInlineActions(
  props: LibraryInlineActionsProps & {configuration: Configuration},
) {
  const {configuration} = props;
  const canEditConfiguration = true;
  return (
    <>{canEditConfiguration && <ConfigurationEditButton configurationId={configuration.id} />}</>
  );
}

export {ConfigurationInlineActions};
