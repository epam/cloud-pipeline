import type {ComponentType} from 'react';
import {useLocation} from 'react-router-dom';
import CoreNodes from '../../components/cluster/core-nodes';
import CloudNodes from '../../components/cluster/cloud-nodes';
import HotCluster from '../../components/cluster/hot-node-pool';
import HotClusterUsage from '../../components/cluster/hot-node-pool/hot-cluster-usage';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

const sectionComponents: Record<string, ComponentType<Record<string, unknown>>> = {
  'core-nodes': CoreNodes,
  'cloud-nodes': CloudNodes,
  hot: HotCluster,
  usage: HotClusterUsage,
};

function ClusterSectionPage() {
  const {pathname} = useLocation();
  const sectionKey = pathname.split('/').pop() ?? 'cluster';
  const Component = sectionComponents[sectionKey];

  if (!Component) {
    return null;
  }

  return <LegacyComponentBridge component={Component as never} />;
}

export {ClusterSectionPage};
