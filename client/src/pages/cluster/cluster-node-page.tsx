import ClusterNode from '../../components/cluster/ClusterNode';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function ClusterNodePage() {
  return <LegacyComponentBridge component={ClusterNode} />;
}

export {ClusterNodePage};
