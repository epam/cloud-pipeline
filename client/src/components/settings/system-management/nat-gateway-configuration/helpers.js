/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Popover, Tooltip} from 'antd';
import classNames from 'classnames';
import NATRouteStatuses from './route-statuses';
import * as portUtilities from './ports-utilities';
import highlightText from '../../../special/highlightText';
import styles from './nat-gateway-configuration.css';

const validationConfig = {
  port: {
    min: 1,
    max: 65535,
    total: 50
  },
  ip: new RegExp(/^(?!0)(?!.*\.$)((1?\d?\d|25[0-5]|2[0-4]\d)(\.|$)){4}$/),
  server: /^[a-z]([0-9a-z-_]{0,61}[0-9a-z_])?$/g,
  messages: {
    required: 'Field is required',
    invalid: 'Invalid format',
    duplicate: 'Value should be unique',
    lengthExceed: 'Maximum length exceeded. Expected length should be less than 253 characters'
  }
};

export function validatePort (value, otherPorts) {
  const {port: portConfig} = validationConfig;
  try {
    const ports = portUtilities.parsePorts(
      value,
      {
        throwError: true,
        maxPorts: portConfig.total
      });
    if (ports.length === 0) {
      throw new Error('Port is required');
    }
    const other = [];
    for (const otherPortConfiguration of otherPorts) {
      const parsed = portUtilities.parsePorts(
        otherPortConfiguration,
        {maxPorts: portConfig.total, throwError: {maxPorts: true}}
      );
      for (const otherPort of parsed) {
        other.push(otherPort);
      }
    }
    let total = 0;
    for (const port of ports) {
      total += 1;
      if (total > portConfig.total) {
        throw new Error(`${portConfig.total} total ports are allowed`);
      }
      if (port < portConfig.min || port > portConfig.max) {
        throw new Error(`Port should be in range ${portConfig.min}-${portConfig.max}`);
      }
      if (other.find(o => o === port)) {
        throw new Error(`Duplicate port ${port}`);
      }
    }
    if (total + other.length > portConfig.total) {
      throw new Error(`${portConfig.total} total ports are allowed`);
    }
  } catch (e) {
    return {
      error: true,
      message: e.message
    };
  }
  return {error: false};
}

export function validateServerName (value) {
  const {messages, server} = validationConfig;
  if (!value || !value.trim()) {
    return {error: true, message: messages.required};
  }
  if (!value.split('.').every(v => v.match(server))) {
    return {error: true, message: messages.invalid};
  }
  if (value.length > 253) {
    return {error: true, message: messages.lengthExceed};
  }
  return {error: false};
}

export function validateIP (value, skip = false) {
  const {ip, messages} = validationConfig;
  if (skip) {
    return {error: false};
  }
  if (!value) {
    return {error: true, message: messages.required};
  }
  if (!ip.test(value)) {
    return {error: true, message: messages.invalid};
  }
  return {error: false};
}

export function validateDescription (value) {
  const {messages} = validationConfig;
  if (value && value.toString().length > 253) {
    return {error: true, message: messages.lengthExceed};
  } else {
    return {error: false};
  }
}

function renderStatusIcon (status) {
  switch (status) {
    case NATRouteStatuses.ACTIVE:
      return (
        <Tooltip title={status}>
          <Icon
            className={
              classNames(
                styles.routeStatus,
                'cp-nat-route-status',
                'cp-primary'
              )
            }
            type="play-circle-o"
          />
        </Tooltip>
      );
    case NATRouteStatuses.CREATION_SCHEDULED:
      return (
        <Tooltip title={status}>
          <Icon
            type="hourglass"
            className={classNames(styles.routeStatus, 'cp-nat-route-status', 'cp-primary')}
          />
        </Tooltip>
      );
    case NATRouteStatuses.PENDING:
    case NATRouteStatuses.SERVICE_CONFIGURED:
    case NATRouteStatuses.DNS_CONFIGURED:
    case NATRouteStatuses.PORT_FORWARDING_CONFIGURED:
      return (
        <Tooltip title={status}>
          <Icon
            type="loading"
            className={classNames(styles.routeStatus, 'cp-nat-route-status', 'cp-primary')}
          />
        </Tooltip>
      );
    case NATRouteStatuses.TERMINATION_SCHEDULED:
    case NATRouteStatuses.TERMINATING:
    case NATRouteStatuses.RESOURCE_RELEASED:
    case NATRouteStatuses.TERMINATED:
      return (
        <Tooltip title={status}>
          <Icon
            className={
              classNames(
                styles.routeStatus,
                'cp-nat-route-status',
                'cp-warning',
                styles.blink
              )
            }
            type="clock-circle-o"
          />
        </Tooltip>
      );
    case NATRouteStatuses.FAILED:
      return (
        <Tooltip title={status}>
          <Icon
            className={classNames(styles.routeStatus, 'cp-nat-route-status', 'cp-error')}
            type="exclamation-circle-o" />
        </Tooltip>
      );
    case NATRouteStatuses.UNKNOWN:
      return (
        <Tooltip title={status}>
          <Icon
            className={
              classNames(
                styles.routeStatus,
                'cp-nat-route-status',
                'cp-text-not-important'
              )
            }
            type="question-circle-o" />
        </Tooltip>
      );
    default:
      return (
        <Icon
          className={
            classNames(
              styles.routeStatus,
              'cp-nat-route-status',
              'cp-text-not-important'
            )
          }
          style={{display: 'none'}}
          type="question-circle-o"
        />
      );
  }
}

function renderPorts (value, record, index, search) {
  if (Array.isArray(value)) {
    const MAX_ITEMS_TO_DISPLAY = 5;
    const slicedArray = value.slice(0, MAX_ITEMS_TO_DISPLAY);
    const sliced = value.length > slicedArray.length;
    const Wrapper = ({children}) => {
      if (sliced) {
        const totalSliced = slicedArray
          .map(portUtilities.getPortsCount)
          .reduce((total, current) => total + current, 0);
        const all = value
          .map(portUtilities.getPortsCount)
          .reduce((total, current) => total + current, 0);
        return (
          <div>
            {children}
            <Popover
              content={(
                <div>
                  {
                    value.map(item => (
                      <div key={item}>
                        {highlightText(item, search)}
                      </div>
                    ))
                  }
                </div>
              )}
            >
              <a>
                and {all - totalSliced} more...
              </a>
            </Popover>
          </div>
        );
      }
      return children;
    };
    return (
      <Wrapper>
        <div className={styles.ports}>
          {
            slicedArray.map(row => (
              <span
                className={styles.port}
                key={row}
              >
                {highlightText(row, search)}
              </span>
            ))
          }
        </div>
      </Wrapper>
    );
  }
  return highlightText(value, search);
}

function renderText (text, record, index, search) {
  return highlightText(text, search);
}

export const columns = {
  external: [
    {
      name: 'status',
      prettyName: '',
      className: styles.statusColumn,
      renderer: renderStatusIcon
    },
    {
      name: 'externalName',
      prettyName: 'name',
      className: styles.nameColumn,
      renderer: renderText
    },
    {
      name: 'externalIp',
      prettyName: 'ip',
      className: styles.ipColumn,
      renderer: renderText
    },
    {
      name: 'externalPortsPresentation',
      prettyName: 'ports',
      className: styles.portsColumn,
      renderer: renderPorts
    },
    {
      name: 'protocol',
      prettyName: 'protocol',
      className: styles.protocolColumn,
      renderer: renderText
    }
  ],
  internal: [
    {
      name: 'internalName',
      prettyName: 'service name',
      className: styles.serviceNameColumn,
      renderer: renderText
    },
    {
      name: 'internalIp',
      prettyName: 'ip',
      className: styles.ipColumn,
      renderer: renderText
    },
    {
      name: 'internalPortsPresentation',
      prettyName: 'ports',
      className: styles.portsColumn,
      renderer: renderPorts
    }
  ]
};
