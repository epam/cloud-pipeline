/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {protocolNames} from './protocols';
import NATRouteStatuses from './route-statuses';

/**
 * @typedef {Object} ThrowErrorConfiguration
 * @property {boolean} [required = false]
 * @property {boolean} [maxPorts = false]
 * @property {boolean} [format = false]
 */

/**
 * Parses throw error configuration
 * @param {undefined|boolean|ThrowErrorConfiguration} throwError
 * @return {ThrowErrorConfiguration}
 */
function parseThrowErrorConfiguration (throwError) {
  const {
    required = false,
    maxPorts = false,
    format = false
  } = (typeof throwError === 'boolean')
    ? {required: throwError, maxPorts: throwError, format: throwError}
    : (throwError || {});
  return {
    required,
    maxPorts,
    format
  };
}

/**
 * Parses port configuration. Possible formats
 * @param portEntry
 * @param options
 * @return {Generator<number, void, *>}
 */
function * parsePortEntry (portEntry, options = {}) {
  const {
    throwError = false,
    maxPorts = Infinity
  } = options;
  const {
    required: throwRequired,
    maxPorts: throwMaxPorts,
    format: throwFormat
  } = parseThrowErrorConfiguration(throwError);
  if (portEntry === undefined || portEntry === null) {
    if (throwRequired) {
      throw new Error('Port is required');
    }
  } else if (typeof portEntry === 'number') {
    yield portEntry;
  } else if (typeof portEntry === 'string' && !Number.isNaN(Number(portEntry))) {
    yield Number(portEntry);
  } else if (typeof portEntry === 'string') {
    const e = /^([\d]+)[\s]*-[\s]*([\d]+)$/.exec(portEntry);
    if (e && e.length >= 3) {
      const from = Number(e[1]);
      const to = Number(e[2]);
      if (from > to && throwFormat) {
        throw new Error('Wrong port format');
      }
      if (to - from + 1 > maxPorts) {
        if (throwMaxPorts) {
          throw new Error(`${maxPorts} total ports are allowed`);
        }
      }
      for (let port = from; port <= Math.min(to, from + maxPorts); port++) {
        yield port;
      }
    } else if (throwFormat) {
      throw new Error('Wrong port format');
    }
  }
}

function getPortEntryCount (portEntry) {
  if (portEntry === undefined || portEntry === null) {
    return 0;
  }
  if (typeof portEntry === 'number') {
    return 1;
  }
  if (typeof portEntry === 'string' && !Number.isNaN(Number(portEntry))) {
    return 1;
  }
  if (typeof portEntry === 'string') {
    const e = /^([\d]+)[\s]*-[\s]*([\d]+)$/.exec(portEntry);
    if (e && e.length >= 3) {
      const n1 = Number(e[1]);
      const n2 = Number(e[2]);
      return Math.max(0, n2 - n1 + 1);
    }
  }
  return 1;
}

export function * parsePorts (string, options = {}) {
  const {
    throwError = false,
    maxPorts = Infinity
  } = options;
  const {
    required: throwRequired,
    maxPorts: throwMaxPorts
  } = parseThrowErrorConfiguration(throwError);
  if (!string) {
    if (throwRequired) {
      throw new Error('Port is required');
    }
  } else if (typeof string === 'number') {
    yield string;
  } else if (typeof string === 'string') {
    const parts = string.split(',');
    let o = 0;
    for (const part of parts) {
      o += 1;
      if (o > maxPorts) {
        if (throwMaxPorts) {
          throw new Error(`${maxPorts} total ports are allowed`);
        }
        return;
      }
      yield * parsePortEntry(part, options);
    }
  }
}

export function getPortsCount (string) {
  if (!string) {
    return 0;
  }
  if (typeof string === 'number') {
    return 1;
  }
  if (typeof string === 'string') {
    return string.split(',')
      .map(o => getPortEntryCount(o.trim()))
      .reduce((r, c) => r + c, 0);
  }
  return 0;
}

export function combinePorts (ports = []) {
  const portNumbers = ports
    .filter(p => !Number.isNaN(Number(p)))
    .map(p => Number(p))
    .sort((a, b) => a - b);
  const groups = [];
  for (let i = 0; i < portNumbers.length; i++) {
    const port = portNumbers[i];
    let group = groups.length > 0 ? groups[groups.length - 1] : undefined;
    if (!group || group.end < port - 1) {
      group = {
        start: port,
        end: port
      };
      groups.push(group);
    }
    group.end = port;
  }
  const wrongValues = ports.filter(p => Number.isNaN(Number(p)));
  const format = (group) => {
    if (group.start === group.end) {
      return `${group.start}`;
    }
    return `${group.start}-${group.end}`;
  };
  return groups.map(format).concat(wrongValues);
}

function processProtocolPorts (routes, mapPort = (o => o.externalPort)) {
  const ports = routes.map(mapPort).filter(Boolean);
  const presentation = combinePorts(ports);
  return {
    ports,
    presentation
  };
}

function sortByPorts (a, b) {
  return (a.externalPort || 0) - (b.externalPort || 0);
}

export function groupRoutes (routes) {
  const keys = [];
  for (const route of routes) {
    const {externalIp, internalIp, protocol} = route;
    if (
      !keys.find(o => o.externalIp === externalIp &&
        o.internalIp === internalIp &&
        o.protocol === protocol
      )
    ) {
      keys.push({
        externalIp,
        internalIp,
        protocol
      });
    }
  }
  return keys.map(key => {
    const {
      externalIp,
      internalIp,
      protocol
    } = key;
    const ipRoutes = routes
      .filter(o =>
        o.protocol === protocol &&
        o.externalIp === externalIp &&
        o.internalIp === internalIp
      )
      .sort(sortByPorts);
    const statuses = [...(new Set(ipRoutes.map(o => o.status)))];
    const [any] = ipRoutes;
    const {
      externalPort,
      ...routeConfiguration
    } = any || {};
    const {
      presentation: externalPortsPresentation,
      ports: externalPorts
    } = processProtocolPorts(ipRoutes, o => o.externalPort);
    const {
      presentation: internalPortsPresentation
    } = processProtocolPorts(ipRoutes, o => o.internalPort);
    return {
      ...routeConfiguration,
      status: statuses.length > 1
        ? NATRouteStatuses.PENDING
        : statuses.pop(),
      protocol: protocolNames[protocol] || protocol,
      externalPorts: externalPorts,
      externalPortsPresentation,
      internalPortsPresentation,
      grouped: true,
      routes: ipRoutes,
      children: ipRoutes.length > 1
        ? ipRoutes.map(o => ({
          ...o,
          protocol: protocolNames[protocol] || protocol,
          externalPortsPresentation: o.externalPort,
          internalPortsPresentation: o.internalPort
        }))
        : undefined
    };
  });
}
