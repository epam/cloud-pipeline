import AllRuns from '../../components/runs/AllRuns';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function RunsPage() {
  return <LegacyComponentBridge component={AllRuns} />;
}

export {RunsPage};
