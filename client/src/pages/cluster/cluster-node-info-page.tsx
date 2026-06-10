import {useOutletContext} from 'react-router-dom';
import ClusterNodeGeneralInfo from '../../components/cluster/ClusterNodeGeneralInfo';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

type ClusterNodeOutletContext = {
  node?: unknown;
  chartsData?: unknown;
  nodeName?: string;
  isCloudNode?: boolean;
};

function ClusterNodeInfoPage() {
  const outletContext = useOutletContext<ClusterNodeOutletContext>();

  return (
    <LegacyComponentBridge<ClusterNodeOutletContext>
      component={ClusterNodeGeneralInfo as never}
      componentProps={{
        node: outletContext?.node,
        chartsData: outletContext?.chartsData,
        nodeName: outletContext?.nodeName,
        isCloudNode: outletContext?.isCloudNode,
      }}
    />
  );
}

export {ClusterNodeInfoPage};
