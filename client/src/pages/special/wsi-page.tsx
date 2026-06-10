import VSIPreviewPage from '../../components/applications/vsi-preview';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function WsiPage() {
  return <LegacyComponentBridge component={VSIPreviewPage} />;
}

export {WsiPage};
