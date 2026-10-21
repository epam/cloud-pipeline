/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {SERVER} from '../../config';
import styles from './styles.css';
import BashCode from '../special/bash-code';
import DriveMappingWindowsForm from './DriveMappingWindowsForm';

// matches {smb:https://.../smb/auth} and {smb.smb_password},
// generalized for any {AAA:<url>} / {AAA.<property>}
const PLACEHOLDER_REGEXP = /\{([a-zA-Z][\w-]*)([.:])([^{}]+)\}/g;
const WINDOWS_AUTH_TEMPLATE_MARKER = /^<AUTH_TEMPLATE>$/i;

function asArray (value) {
  if (value === undefined || value === null) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}

function resolveUrl (url) {
  if (!url || /^([a-z]+:)?\/\//i.test(url)) {
    return url;
  }
  const base = SERVER || '';
  if (base.endsWith('/') && url.startsWith('/')) {
    return base + url.slice(1);
  }
  if (!base.endsWith('/') && !url.startsWith('/')) {
    return `${base}/${url}`;
  }
  return base + url;
}

// splits a {prefix.<property path>} property path on unquoted dots, so it can address
// nested response properties, e.g. "data.nested.value". A segment may be quoted with
// ", ' or ` to allow literal dots/spaces inside it, e.g. data."a.key with space".nested
function parsePropertyPath (path) {
  const segments = [];
  let i = 0;
  while (i < path.length) {
    const quote = path[i];
    if (quote === '"' || quote === '\'' || quote === '`') {
      let j = i + 1;
      while (j < path.length && path[j] !== quote) {
        j += 1;
      }
      segments.push(path.slice(i + 1, j));
      i = (path[j + 1] === '.') ? j + 2 : j + 1;
    } else {
      let j = i;
      while (j < path.length && path[j] !== '.') {
        j += 1;
      }
      segments.push(path.slice(i, j));
      i = j + 1;
    }
  }
  return segments;
}

function resolvePropertyPath (data, path) {
  return parsePropertyPath(path).reduce(
    (current, segment) => (current === undefined || current === null)
      ? undefined
      : current[segment],
    data
  );
}

// splits a {prefix.<property path>:<configuration>} placeholder body on the first
// unquoted colon, so the property path (which may itself use quoted segments) is
// never confused with the trailing configuration, e.g. "expires_in:duration:sec"
// -> property "expires_in", configuration "duration:sec"
function splitPropertyAndConfig (rest) {
  let i = 0;
  while (i < rest.length) {
    const char = rest[i];
    if (char === '"' || char === '\'' || char === '`') {
      let j = i + 1;
      while (j < rest.length && rest[j] !== char) {
        j += 1;
      }
      i = j + 1;
    } else if (char === ':') {
      return {property: rest.slice(0, i), configuration: rest.slice(i + 1)};
    } else {
      i += 1;
    }
  }
  return {property: rest, configuration: undefined};
}

const DURATION_UNIT_TO_MS = {
  ms: 1,
  second: 1000,
  seconds: 1000,
  sec: 1000,
  s: 1000,
  minute: 60000,
  minutes: 60000,
  min: 60000,
  m: 60000,
  hour: 3600000,
  hours: 3600000,
  h: 3600000,
  day: 86400000,
  days: 86400000,
  d: 86400000
};

const DURATION_COMPONENTS = [
  {ms: 86400000, singular: 'day', plural: 'days'},
  {ms: 3600000, singular: 'hour', plural: 'hours'},
  {ms: 60000, singular: 'minute', plural: 'minutes'},
  {ms: 1000, singular: 'second', plural: 'seconds'},
  {ms: 1, singular: 'millisecond', plural: 'milliseconds'}
];

// formats `value` (a number, in `sourceUnit`) as a human-readable duration, e.g.
// (181, 'sec') -> "3 minutes, 1 second". Only the largest non-zero component and,
// if it is itself non-zero, the very next (adjacent) smaller component are shown -
// e.g. (86401, 'sec') -> "1 day" (the leftover second is dropped, being non-adjacent).
function formatDuration (value, sourceUnit) {
  const msPerUnit = DURATION_UNIT_TO_MS[sourceUnit];
  const numeric = Number(value);
  if (!msPerUnit || Number.isNaN(numeric)) {
    return undefined;
  }
  let remainder = Math.round(numeric * msPerUnit);
  const counts = DURATION_COMPONENTS.map(({ms}) => {
    const count = Math.floor(remainder / ms);
    remainder -= count * ms;
    return count;
  });
  const topIndex = counts.findIndex((count) => count > 0);
  if (topIndex === -1) {
    const last = DURATION_COMPONENTS[DURATION_COMPONENTS.length - 1];
    return `0 ${last.plural}`;
  }
  const indexes = [topIndex];
  if (counts[topIndex + 1] > 0) {
    indexes.push(topIndex + 1);
  }
  return indexes
    .map((index) => {
      const count = counts[index];
      const {singular, plural} = DURATION_COMPONENTS[index];
      return `${count} ${count === 1 ? singular : plural}`;
    })
    .join(', ');
}

// applies a {prefix.property:<configuration>} configuration to a resolved value.
// Only "duration:<unit>" is currently supported (unit is one of ms,
// second/seconds/sec/s, minute/minutes/min/m, hour/hours/h, day/days/d);
// unknown configurations leave the value unchanged.
function applyConfiguration (value, configuration) {
  if (!configuration) {
    return value;
  }
  const [type, ...params] = configuration.split(':');
  if (type === 'duration') {
    const formatted = formatDuration(value, params[0]);
    if (formatted !== undefined) {
      return formatted;
    }
  }
  return value;
}

// only "smb" has a default endpoint (for backwards compatibility); any other prefix
// without an explicit {prefix:<url>} is ignored entirely (no fetch, its {prefix.*}
// placeholders resolve to an empty string, same as a failed request)
const DEFAULT_ENDPOINTS = {smb: '/smb/auth'};

// scans all strings and, for every {prefix:...}/{prefix....} found, resolves the auth endpoint:
// the last {prefix:<url>} wins
function extractEndpoints (strings) {
  const endpoints = {};
  strings.forEach((string) => {
    const regexp = new RegExp(PLACEHOLDER_REGEXP);
    let match;
    while ((match = regexp.exec(string))) {
      const [, prefix, separator, rest] = match;
      if (!endpoints.hasOwnProperty(prefix)) {
        endpoints[prefix] = undefined;
      }
      if (separator === ':') {
        endpoints[prefix] = rest;
      }
    }
  });
  Object.keys(endpoints).forEach((prefix) => {
    if (!endpoints[prefix]) {
      if (DEFAULT_ENDPOINTS[prefix]) {
        endpoints[prefix] = DEFAULT_ENDPOINTS[prefix];
      } else {
        delete endpoints[prefix];
      }
    }
  });
  return endpoints;
}

@inject('preferences')
@observer
export default class FileSystemAccess extends React.Component {
  state = {
    resolved: {},
    pending: false
  };

  componentDidMount () {
    this.fetchEndpoints();
  }

  componentDidUpdate (prevProps) {
    if (
      prevProps.content !== this.props.content ||
      prevProps.userJwtToken !== this.props.userJwtToken
    ) {
      this.fetchEndpoints();
    }
  }

  get preparedContent () {
    const {preferences, userJwtToken, content} = this.props;
    return asArray(content)
      .map(o => (o === undefined || o === null) ? '' : String(o))
      .map(o => preferences ? preferences.replacePlaceholders(o) : o)
      .map(o => o.replace(/\{user\.jwt\.token\}/g, userJwtToken || ''));
  }

  fetchEndpoints = () => {
    const {userJwtToken} = this.props;
    const endpoints = extractEndpoints(this.preparedContent);
    const prefixes = Object.keys(endpoints);
    if (prefixes.length === 0) {
      this.setState({resolved: {}, pending: false});
      return;
    }
    this.setState({pending: true});
    Promise.all(prefixes.map((prefix) => fetch(
      resolveUrl(endpoints[prefix]),
      {
        method: 'POST',
        mode: 'cors',
        credentials: 'include',
        headers: {
          Authorization: `Bearer ${userJwtToken || ''}`
        }
      }
    )
      .then((response) => response.ok
        ? response.json()
        : Promise.reject(new Error(`Error fetching "${prefix}" auth info`)))
      .then((data) => ({prefix, data}))
      .catch(() => ({prefix, data: undefined}))
    ))
      .then((results) => {
        const resolved = {};
        results.forEach(({prefix, data}) => {
          resolved[prefix] = data;
        });
        this.setState({resolved, pending: false});
      });
  };

  renderSingle = (raw, key) => {
    const {pending, resolved} = this.state;
    if (pending) {
      return (
        <BashCode
          id="file-system-access-command"
          key={key}
          loading
          className={styles.mdPreview}
          copyable
        />
      );
    }
    const code = raw.replace(
      new RegExp(PLACEHOLDER_REGEXP),
      (match, prefix, separator, rest) => {
        if (separator === ':') {
          return '';
        }
        const {property, configuration} = splitPropertyAndConfig(rest);
        const data = resolved[prefix];
        const value = data === undefined ? undefined : resolvePropertyPath(data, property);
        if (value === undefined || value === null) {
          return '';
        }
        const formatted = applyConfiguration(value, configuration);
        return typeof formatted === 'object' ? JSON.stringify(formatted) : String(formatted);
      }
    );
    if (!code) {
      return null;
    }
    return (
      <BashCode
        id="file-system-access-command"
        key={key}
        className={styles.mdPreview}
        code={code}
        copyable
      />
    );
  };

  render () {
    const strings = this.preparedContent;
    if (strings.length === 0) {
      return null;
    }
    return (
      <div>
        {strings.map((string, index) => WINDOWS_AUTH_TEMPLATE_MARKER.test(string)
          ? (<DriveMappingWindowsForm key={`file-system-access-windows-form-${index}`} />)
          : this.renderSingle(string, `file-system-access-content-${index}`)
        )}
      </div>
    );
  }
}

FileSystemAccess.propTypes = {
  userJwtToken: PropTypes.string,
  content: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.arrayOf(PropTypes.string)
  ])
};
