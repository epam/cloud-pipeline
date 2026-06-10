import LogsRedirect from '../../components/runs/logs/logs-redirect';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function RunDetailsPage() {
  return <LegacyComponentBridge component={LogsRedirect} />;
}

export {RunDetailsPage};
