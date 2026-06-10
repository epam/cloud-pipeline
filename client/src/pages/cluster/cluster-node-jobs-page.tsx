import {useOutletContext} from 'react-router-dom';
import ClusterNodePods from '../../components/cluster/ClusterNodePods';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type ClusterNodeOutletContext = {
  node?: unknown;
  chartsData?: unknown;
  nodeName?: string;
  isCloudNode?: boolean;
};

function ClusterNodeJobsPage() {
  const outletContext = useOutletContext<ClusterNodeOutletContext>();

  return (
    <LegacyComponentBridge<ClusterNodeOutletContext>
      component={ClusterNodePods as never}
      componentProps={{
        node: outletContext?.node,
        chartsData: outletContext?.chartsData,
        nodeName: outletContext?.nodeName,
        isCloudNode: outletContext?.isCloudNode,
      }}
    />
  );
}

export {ClusterNodeJobsPage};
