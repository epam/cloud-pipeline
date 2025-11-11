import React from 'react';
import RunCloudRegion from './cloud-region';
import ExecEnvironment from './exec-environment';
import RunCmd from './run-cmd';
import RunCmdTemplate from './run-cmd-template';
import RunCores from './run-cores';
import RunNodeDisk from './run-node-disk';
import RunDockerImage from './run-docker-image';
import RunIpAddress from './run-ip-address';
import RunNodeImage from './run-node-image';
import RunNodeType from './run-node-type';
import RunPriceType from './run-price-type';

const instanceParameters = [
  {
    key: 'execEnvironment',
    title: 'Execution environment',
    available: ExecEnvironment.available,
    render: (run, props) => (
      <ExecEnvironment {...props} run={run} />
    )
  },
  {
    key: 'runCloudRegion',
    title: 'Cloud region',
    available: RunCloudRegion.available,
    render: (run, props) => (
      <RunCloudRegion {...props} run={run} />
    )
  },
  {
    key: 'runNodeType',
    title: 'Node type',
    available: RunNodeType.available,
    render: (run, props) => (
      <RunNodeType {...props} run={run} />
    )
  },
  {
    key: 'runPriceType',
    title: 'Price type',
    available: RunPriceType.available,
    render: (run, props) => (
      <RunPriceType {...props} run={run} />
    )
  },
  {
    key: 'runDisk',
    title: 'Disk',
    available: RunNodeDisk.available,
    render: (run, props) => (
      <RunNodeDisk {...props} run={run} />
    )
  },
  {
    key: 'runCores',
    title: 'Cores',
    available: RunCores.available,
    render: (run, props) => (
      <RunCores {...props} run={run} />
    )
  },
  {
    key: 'runIpAddress',
    title: 'IP',
    available: RunIpAddress.available,
    render: (run, props) => (
      <RunIpAddress {...props} run={run} />
    )
  },
  {
    key: 'runDockerImage',
    title: 'Docker image',
    available: RunDockerImage.available,
    render: (run, props) => (
      <RunDockerImage {...props} run={run} />
    )
  },
  {
    key: 'runNodeImage',
    title: 'Node image',
    available: RunNodeImage.available,
    render: (run, props) => (
      <RunNodeImage {...props} run={run} />
    )
  },
  {
    key: 'runCmdTemplate',
    title: 'Cmd template',
    available: RunCmdTemplate.available,
    render: (run, props) => (
      <RunCmdTemplate {...props} run={run} />
    )
  },
  {
    key: 'runCmd',
    title: 'Cmd',
    available: RunCmd.available,
    render: (run, props) => (
      <RunCmd {...props} run={run} />
    )
  }
];

export default instanceParameters;
