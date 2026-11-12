/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

'use strict';

// webpack 4 does not read the "exports" field of package.json. The AWS SDK v3 packages
// (@aws-sdk/*, @smithy/*) import their own subpaths, such as "@smithy/core/serde". For these,
// webpack 4 finds the package's CommonJS redirect file (serde.js), which loads the Node.js build
// (dist-cjs) and its "node:" modules. resolveSubpathImport points such an import at the file that
// "exports" names for a browser: the browser ES module build.

const fs = require('fs');
const path = require('path');

const AWS_SDK_SUBPATH_IMPORT = /^@(aws-sdk|smithy)\/[^/]+\/.+$/;
const CONDITIONS = ['browser', 'module', 'import', 'default'];

const exportsCache = new Map();

function readExports (packageJson) {
  if (!exportsCache.has(packageJson)) {
    exportsCache.set(packageJson, JSON.parse(fs.readFileSync(packageJson, 'utf8')).exports);
  }
  return exportsCache.get(packageJson);
}

function findPackageJson (context, packageName) {
  let directory = context;
  for (;;) {
    const packageJson = path.join(directory, 'node_modules', packageName, 'package.json');
    if (fs.existsSync(packageJson)) {
      return packageJson;
    }
    const parent = path.dirname(directory);
    if (parent === directory) {
      return undefined;
    }
    directory = parent;
  }
}

function pickTarget (entry) {
  if (typeof entry === 'string') {
    return entry;
  }
  if (entry && typeof entry === 'object') {
    for (const condition of CONDITIONS) {
      const target = pickTarget(entry[condition]);
      if (target) {
        return target;
      }
    }
  }
  return undefined;
}

// A NormalModuleReplacementPlugin callback: changes resource.request in place.
function resolveSubpathImport (resource) {
  const [scope, name, ...subpath] = resource.request.split('/');
  const packageJson = findPackageJson(resource.context, `${scope}/${name}`);
  if (!packageJson) {
    return;
  }
  const exportsMap = readExports(packageJson);
  const target = exportsMap && typeof exportsMap === 'object'
    ? pickTarget(exportsMap[`./${subpath.join('/')}`])
    : undefined;
  if (target) {
    resource.request = path.join(path.dirname(packageJson), target);
  }
}

module.exports = {AWS_SDK_SUBPATH_IMPORT, resolveSubpathImport};
