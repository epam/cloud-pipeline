import Tool from '../../components/tools/Tool';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function ToolPage() {
  return <LegacyComponentBridge component={Tool} />;
}

export {ToolPage};
