import LaunchPipeline from '../../components/pipelines/launch/LaunchPipeline';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function LaunchPage() {
  return <LegacyComponentBridge component={LaunchPipeline} />;
}

export {LaunchPage};
