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
import classNames from 'classnames';
import AdaptedLink from '../special/AdaptedLink';
import {
  nodeRoles,
  parseLabel,
  roleIsDefined,
  testRole
} from './node-roles';

export function renderNodeLabels (labels, config) {
  if (!labels) {
    return null;
  }
  const {
    onlyKnown = false,
    pipelineRun,
    className,
    additionalStyle,
    location,
    sortFn = (a, b) => {}
  } = config;
  const renderItems = [];
  const displayTag =
    (label, value, role = 0) =>
      <span
        id={`label-${label}`}
        className={
          classNames(
            className,
            'cp-node-tag',
            {
              'cp-node-tag-run': testRole(role, nodeRoles.run),
              'cp-node-tag-master': testRole(role, nodeRoles.master),
              'cp-node-tag-cp-role': testRole(role, nodeRoles.cloudPipelineRole),
              'cp-node-tag-pipeline-info': testRole(role, nodeRoles.pipelineInfo),
              'cp-node-tag-pool': testRole(role, nodeRoles.nodePoolRole)
            }
          )
        }
        style={{...additionalStyle}}
        key={label}
        data-run={testRole(role, nodeRoles.run)}
        data-master={testRole(role, nodeRoles.master)}
        data-cloud-pipeline-role={testRole(role, nodeRoles.cloudPipelineRole)}
        data-pipeline-info={testRole(role, nodeRoles.pipelineInfo)}
        data-pool={testRole(role, nodeRoles.nodePoolRole)}
        data-label={label}
      >
        {value.toUpperCase()}
      </span>;
  const labelsInfo = Object.keys(labels)
    .map(key => ({
      key,
      info: parseLabel(key, labels[key], config)
    }))
    .sort(sortFn);
  labelsInfo.forEach(({key, info}) => {
    if (testRole(info.role, nodeRoles.run)) {
      const labelKey = pipelineRun ? key.toUpperCase() : 'default';
      if (location && pipelineRun) {
        renderItems.push(
          <AdaptedLink
            id="label-link-run-id"
            key={key}
            to={`/run/${pipelineRun.id}`}
            location={location}
          >
            {displayTag(labelKey, info.value, info.role)}
          </AdaptedLink>
        );
      } else {
        renderItems.push(displayTag(key, info.value, info.role));
      }
    } else if (!onlyKnown || roleIsDefined(info.role)) {
      renderItems.push(displayTag(info.name, info.value, info.role));
    }
  });
  return renderItems.reduce((array, item) => ([...array, ' ', item]), []);
}
