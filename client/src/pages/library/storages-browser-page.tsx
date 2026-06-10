import Browser from '../../components/pipelines/browser/Browser';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function StoragesBrowserPage() {
  return (
    <LegacyComponentBridge component={Browser} componentProps={{browserLocation: 'storages'}} />
  );
}

export {StoragesBrowserPage};
