import React from 'react';
import PropTypes from 'prop-types';
import LoadingView from '../../special/LoadingView.tsx';
import GpuInfo from './gpu-info';
import {getInstanceTypeInfo} from './utils';

class GpuInfoHoc extends React.Component {
  state = {
    gpuStatisticsAvailable: false,
    gpuStatisticsPending: false,
  };

  componentDidMount() {
    this.checkGpuStatisticsAvailable();
  }

  componentWillUnmount() {
    this.token = {};
  }

  checkGpuStatisticsAvailable = () => {
    const {instanceType} = this.props;
    const token = (this.token = {});
    const commit = (st) => {
      if (token === this.token) {
        this.setState(st);
      }
    };
    (async () => {
      try {
        commit({gpuStatisticsAvailable: false, gpuStatisticsPending: true});
        const info = await getInstanceTypeInfo(instanceType);
        const {gpu, gpuDevice} = info || {};
        const gpuStatisticsAvailable = instanceType ? Boolean(gpu || gpuDevice) : true;
        commit({gpuStatisticsAvailable, gpuStatisticsPending: false});
      } catch {
        commit({gpuStatisticsAvailable: false, gpuStatisticsPending: false});
      }
    })();
  };

  render() {
    const {gpuStatisticsAvailable, gpuStatisticsPending} = this.state;
    const {nodeName, chartsData, node, router} = this.props;
    if (gpuStatisticsPending) {
      return <LoadingView />;
    }
    return (
      <GpuInfo
        node={node}
        gpuStatisticsAvailable={gpuStatisticsAvailable}
        chartsData={chartsData}
        nodeName={nodeName}
        router={router}
      />
    );
  }
}

GpuInfoHoc.propTypes = {
  nodeName: PropTypes.string,
  instanceType: PropTypes.string,
  chartsData: PropTypes.object,
  node: PropTypes.object,
  router: PropTypes.object,
};

export default GpuInfoHoc;
