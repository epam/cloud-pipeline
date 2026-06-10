import Browser from '../../components/pipelines/browser/Browser';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function PipelinesBrowserPage() {
  return (
    <LegacyComponentBridge component={Browser} componentProps={{browserLocation: 'pipelines'}} />
  );
}

export {PipelinesBrowserPage};
