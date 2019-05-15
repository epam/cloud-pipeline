/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {Icon, Row, Tooltip} from 'antd';

/*
 * Launch cluster tooltips.
 *
 * 4 tooltips are presented for 'Launch cluster' dialog:
 *  - Common tooltip for popup header (`CLUSTER_MODES_TOOLTIP`)
 *  - Enable grid engine tooltip for 'Cluster' tab (`ENABLE_GRID_ENGINE_TOOLTIP`)
 *  - Auto-scaled up to & default child nodes tooltips for 'Auto-scaled cluster' tab
 *    (`AUTOSCALED_CLUSTER_UP_TO_TOOLTIP`, `AUTOSCALED_CLUSTER_DEFAULT_NODES_COUNT_TOOLTIP`)
 *
 * You can use both text & markdown (html) for tooltips.
 * Tooltip won't be shown if no value is presented (`undefined`).
 */

const CLUSTER_MODES_TOOLTIP = (
  <div>
    <Row><h3 style={{color: 'white'}}>Cluster modes</h3></Row>
    <ul>
      <li><b>Single node</b>: single node will be launched</li>
      <li><b>Cluster</b>: a cluster with fixed child nodes will be launched</li>
      <li><b>Auto-scaled cluster</b>: you can configure default and maximum child nodes count</li>
    </ul>
  </div>
);

const ENABLE_GRID_ENGINE_TOOLTIP = 'Enable grid engine';
const AUTOSCALED_CLUSTER_UP_TO_TOOLTIP = undefined;
const AUTOSCALED_CLUSTER_DEFAULT_NODES_COUNT_TOOLTIP = undefined;

export const LaunchClusterTooltip = {
  clusterMode: 'cluster mode',
  cluster: {
    enableGridEngine: 'enable grid engine'
  },
  autoScaledCluster: {
    autoScaledUpTo: 'up to',
    defaultNodesCount: 'default nodes count'
  }
};

const tooltips = {
  [LaunchClusterTooltip.clusterMode]:
    CLUSTER_MODES_TOOLTIP,
  [LaunchClusterTooltip.cluster.enableGridEngine]:
    ENABLE_GRID_ENGINE_TOOLTIP,
  [LaunchClusterTooltip.autoScaledCluster.autoScaledUpTo]:
    AUTOSCALED_CLUSTER_UP_TO_TOOLTIP,
  [LaunchClusterTooltip.autoScaledCluster.defaultNodesCount]:
    AUTOSCALED_CLUSTER_DEFAULT_NODES_COUNT_TOOLTIP
};

export function renderTooltip (tooltip, style) {
  if (!tooltips[tooltip]) {
    return null;
  }
  return (
    <Tooltip title={tooltips[tooltip]}>
      <Icon
        type="question-circle"
        style={style} />
    </Tooltip>
  );
}
