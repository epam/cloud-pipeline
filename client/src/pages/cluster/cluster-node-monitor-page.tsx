import {useOutletContext} from 'react-router-dom';
import ClusterNodeMonitor from '../../components/cluster/cluster-node-monitor';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type ClusterNodeOutletContext = {
  node?: unknown;
  chartsData?: unknown;
  nodeName?: string;
  isCloudNode?: boolean;
};

function ClusterNodeMonitorPage() {
  const outletContext = useOutletContext<ClusterNodeOutletContext>();

  return (
    <LegacyComponentBridge<ClusterNodeOutletContext>
      component={ClusterNodeMonitor as never}
      componentProps={{
        node: outletContext?.node,
        chartsData: outletContext?.chartsData,
        nodeName: outletContext?.nodeName,
        isCloudNode: outletContext?.isCloudNode,
      }}
    />
  );
}

export {ClusterNodeMonitorPage};
