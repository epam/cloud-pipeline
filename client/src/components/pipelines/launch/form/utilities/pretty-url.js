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

const endpointURIMask = /^[a-zA-Z\d_]+$/;
const domainMask = /^[-a-zA-Z0-9_\.]+(:\d+)?$/i;

function parse (value) {
  if (!value) {
    return null;
  }
  if (typeof value === 'object') {
    const {domain, path} = value;
    return [domain, path].filter(Boolean).join('/');
  }
  return value;
}

function validate (url) {
  if (!url) {
    return undefined;
  }
  const {domain, path} = build(url);
  if (
    domain &&
    (
      !domainMask.test(domain) ||
      domain.indexOf('..') >= 0 ||
      domain.indexOf('.') === -1
    )
  ) {
    return 'Please enter valid domain name (only characters, numbers, \'-\', \'_\' and dots symbols are allowed)';
  }
  if (path && !endpointURIMask.test(path)) {
    return 'Please enter valid endpoint name (only characters, numbers and \'_\' symbols are allowed)';
  }
  return undefined;
}

function build (value) {
  if (!value) {
    return undefined;
  }
  const [domain, ...path] = value.split('/');
  if (!path || path.length === 0) {
    // pretty url: "some-string-here"
    if (endpointURIMask.test(domain)) {
      // this is friendly-url part
      return {
        path: domain
      };
    }
    return {
      domain
    };
  }
  return {
    domain,
    path: path.join('/')
  };
}

export {build, parse, validate};
