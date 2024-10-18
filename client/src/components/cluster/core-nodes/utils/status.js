/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Badge} from 'antd';
import highlightText from '../../../special/highlightText';

function serviceStatusRenderer (service) {
  let status = 'success';
  if (service.status.pending) {
    status = 'warning';
  }
  if (service.status.unhealthy) {
    status = 'error';
  }
  return (
    <span>
      <Badge status={status} />
      {service.status.healthy + service.status.pending}/{service.children.length}
    </span>
  );
};

function podStatusRenderer (status, filters = {}) {
  const {globalSearch} = filters;
  if (status) {
    let badge = null;
    switch (status.toUpperCase()) {
      case 'RUNNING':
        badge = <Badge status="processing" />;
        break;
      case 'FAILED':
        badge = <Badge status="error" />;
        break;
      case 'PENDING':
        badge = <Badge status="warning" />;
        break;
      case 'SUCCEEDED':
        badge = <Badge status="success" />;
        break;
      default:
        badge = <Badge status="default" />;
        break;
    }
    return <span>{badge}{highlightText(status, globalSearch)}</span>;
  }
  return null;
};

function podContainerStatusRenderer (status) {
  if (status) {
    const containerStatus = status.status;
    let statusDescription = containerStatus;
    if (status.reason) {
      statusDescription = `${containerStatus} (${status.reason})`;
    }
    if (containerStatus === null) {
      return <Badge status="default" />;
    } else {
      let badge = null;
      switch (containerStatus.toUpperCase()) {
        case 'RUNNING':
          badge = <Badge status="processing" />;
          break;
        case 'TERMINATED':
          if (status.reason && status.reason.toUpperCase() === 'COMPLETED') {
            badge = <Badge status="success" />;
          } else {
            badge = <Badge status="error" />;
          }
          break;
        case 'WAITING':
          badge = <Badge status="warning" />;
          break;
        default:
          badge = <Badge status="default" />;
          break;
      }
      return <span>{badge}{statusDescription}</span>;
    }
  }
  return null;
};

function statusRenderer (status, item, filters) {
  if (item.isService) {
    return serviceStatusRenderer(item);
  }
  if (item.isContainer) {
    return podContainerStatusRenderer(status);
  }
  return podStatusRenderer(status, filters);
};

export default statusRenderer;
