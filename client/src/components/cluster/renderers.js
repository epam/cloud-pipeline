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
import AdaptedLink from '../special/AdaptedLink';

export const PIPELINE_INFO_LABEL = 'pipeline-info';

export function renderNodeLabels (labels, config) {
  if (!labels) {
    return null;
  }
  const {onlyKnown = false, pipelineRun, className, additionalStyle, location} = config;
  const renderItems = [];
  const displayTag =
    (label, value) =>
      <span
        id={`label-${label}`}
        className={className}
        style={{...additionalStyle}}
        key={label}
        label={label.toUpperCase()}>
        {value.toUpperCase()}
      </span>;
  for (let key in labels) {
    if (labels.hasOwnProperty(key)) {
      if (key.toUpperCase() === 'RUNID') {
        const labelKey = pipelineRun ? key.toUpperCase() : 'default';
        if (location && pipelineRun) {
          renderItems.push(
            <AdaptedLink id="label-link-run-id" key={key} to={`/run/${pipelineRun.id}`} location={location}>
              {displayTag(labelKey, `RUN ID ${labels[key]}`)}
            </AdaptedLink>
          );
        } else {
          renderItems.push(displayTag(key, `RUN ID ${labels[key]}`));
        }
      } else if (key.toUpperCase() === 'NODE-ROLE.KUBERNETES.IO/MASTER') {
        renderItems.push(displayTag(key, 'MASTER'));
      } else if (key.toUpperCase() === 'CLOUD-PIPELINE/ROLE') {
        renderItems.push(displayTag(key, labels[key]));
      } else if (
        key.toUpperCase() === 'KUBEADM.ALPHA.KUBERNETES.IO/ROLE' ||
        key.toUpperCase() === PIPELINE_INFO_LABEL.toUpperCase() || !onlyKnown) {
        renderItems.push(displayTag(key, labels[key]));
      }
    }
  }
  return renderItems;
}
