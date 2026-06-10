import MiewViewerPage from '../../components/applications/miew/MiewPage';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function MiewPage() {
  return <LegacyComponentBridge component={MiewViewerPage} />;
}

export {MiewPage};
