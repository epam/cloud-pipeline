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
import StatusIcon from '../../../../special/run-status-icon';
import {Icon, Popover, Row} from 'antd';
import moment from 'moment-timezone';
import parseRunServiceUrl from '../../../../../utils/parseRunServiceUrl';
import evaluateRunDuration from '../../../../../utils/evaluateRunDuration';
import {getRunSpotTypeName} from '../../../../special/spot-instance-names';
import AWSRegionTag from '../../../../special/AWSRegionTag';
import JobEstimatedPriceInfo from '../../../../special/job-estimated-price-info';
import styles from './CardsPanel.css';
import RunTags from '../../../../runs/run-tags';
import PlatformIcon from '../../../../tools/platform-icon';

function renderTitle (run) {
  const podId = run.podId;
  let nodeType, priceType, nodeDisk, nodeCount;
  if (run.instance) {
    priceType = getRunSpotTypeName(run).toLowerCase();
    nodeType = run.instance.nodeType;
    nodeDisk = run.instance.nodeDisk ? `${run.instance.nodeDisk} Gb` : undefined;
  }
  nodeCount = run.nodeCount ? `cluster (${run.nodeCount + 1} nodes)` : undefined;
  return [
    podId,
    nodeCount,
    nodeType,
    priceType,
    nodeDisk
  ].filter(i => !!i).join(', ');
}

function renderPipeline (run) {
  const {pipelineName} = run;
  let displayName;
  if (pipelineName) {
    if (run.version) {
      displayName = `${pipelineName} (${run.version})`;
    } else {
      displayName = pipelineName;
    }
  } else if (run.dockerImage) {
    const parts = run.dockerImage.split('/');
    displayName = parts[parts.length - 1];
  }
  let clusterIcon;
  if (run.nodeCount > 0) {
    clusterIcon = <Icon type="database" />;
  }
  displayName = <span type="main">{displayName}</span>;
  if (run.serviceUrl && run.initialized) {
    const urls = parseRunServiceUrl(run.serviceUrl);
    return (
      <span>
        <StatusIcon run={run} small additionalStyle={{marginRight: 5}} />
        <Popover
          mouseEnterDelay={1}
          content={
            <div>
              <ul>
                {
                  urls.map((url, index) =>
                    <li key={index} style={{margin: 4}}>
                      <a href={url.url} target="_blank">{url.name || url.url}</a>
                    </li>
                  )
                }
              </ul>
            </div>
          }
          trigger="hover">
          <Icon type="export" /> {clusterIcon} {displayName}
        </Popover>
      </span>
    );
  } else {
    return (<span><StatusIcon run={run} small /> {clusterIcon} {displayName}</span>);
  }
}

function renderTime (run) {
  const {startDate} = run;
  if (startDate) {
    return moment.utc(startDate).fromNow(false);
  }
  return null;
}

function renderCommitStatus (run) {
  if (run.commitStatus && run.commitStatus.toLowerCase() === 'committing') {
    return (
      <Row
        style={{fontStyle: 'italic'}}>
        <Icon
          type="code"
          style={{fontWeight: 'large'}}
          className={styles.blink} /> <span style={{fontSize: 'smaller'}}>Committing...</span>
      </Row>
    );
  }
  return undefined;
}

function renderEstimatedPrice (run) {
  if (!run.pricePerHour) {
    return null;
  }
  const diff = evaluateRunDuration(run) * run.pricePerHour;
  const price = (Math.ceil(diff * 100.0) / 100.0) * (run.nodeCount ? (run.nodeCount + 1) : 1);
  return (
    <JobEstimatedPriceInfo>
      , estimated price: <b>{price.toFixed(2)}$</b>
    </JobEstimatedPriceInfo>
  );
}

function renderRegion (run) {
  if (run.instance) {
    const {cloudProvider, cloudRegionId} = run.instance;
    return (
      <AWSRegionTag
        darkMode
        key="region"
        style={{fontSize: 'medium'}}
        provider={cloudProvider}
        regionId={cloudRegionId}
      />
    );
  }
  return null;
}

export default function renderRunCard (run) {
  return [
    <Row key="pipeline" style={{fontWeight: 'bold'}}>
      {renderPipeline(run)}
    </Row>,
    <Row key="title" style={{fontSize: 'smaller'}}>
      {renderTitle(run)}
      {run.sensitive ? ',' : null}
      {run.sensitive ? (<span style={{whiteSpace: 'pre', color: '#ff5c33'}}> sensitive</span>) : null}
    </Row>,
    <Row key="time" style={{fontSize: 'smaller'}}>
      {renderTime(run)}{renderEstimatedPrice(run)}
    </Row>,
    <Row key="commit status">
      {renderCommitStatus(run)}
    </Row>,
    <Row key="region and platform" type="flex" align="middle">
      {renderRegion(run)}
      <PlatformIcon
        platform={run.platform}
      />
    </Row>,
    <RunTags
      key="run-tags"
      run={run}
      onlyKnown
    />
  ];
}
