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

/* eslint-env jest */

'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const {AWS_SDK_SUBPATH_IMPORT, resolveSubpathImport} = require('./aws-sdk-subpath-imports');

// The "exports" of @smithy/core 3.40.0 for "./serde", as published: per-condition targets, nested
// for browser.
const SMITHY_CORE_EXPORTS = {
  '.': {module: './dist-es/index.js', require: './dist-cjs/index.js'},
  './package.json': './package.json',
  './serde': {
    types: './dist-types/submodules/serde/index.d.ts',
    'react-native': {
      import: './dist-es/submodules/serde/index.native.js',
      require: './dist-cjs/submodules/serde/index.native.js'
    },
    browser: {
      import: './dist-es/submodules/serde/index.browser.js',
      require: './dist-cjs/submodules/serde/index.browser.js'
    },
    module: './dist-es/submodules/serde/index.js',
    node: './dist-cjs/submodules/serde/index.js',
    import: './dist-es/submodules/serde/index.js',
    require: './dist-cjs/submodules/serde/index.js'
  }
};

// Made-up packages and subpaths below: a subpath with no browser condition, a package with no
// "exports", and an older nested copy of @smithy/core with a plain string target.
const NO_BROWSER_CONDITION_EXPORTS = {
  './plain': {
    types: './dist-types/submodules/plain/index.d.ts',
    module: './dist-es/submodules/plain/index.js',
    node: './dist-cjs/submodules/plain/index.js',
    import: './dist-es/submodules/plain/index.js',
    require: './dist-cjs/submodules/plain/index.js'
  }
};

function writePackage (root, name, content) {
  const directory = path.join(root, 'node_modules', name);
  fs.mkdirSync(directory, {recursive: true});
  fs.writeFileSync(path.join(directory, 'package.json'), JSON.stringify(content));
  return directory;
}

function resolveFrom (context, request) {
  const resource = {context, request};
  resolveSubpathImport(resource);
  return resource.request;
}

describe('AWS SDK subpath imports for webpack 4', () => {
  let root;
  let smithyCore;
  let noBrowserCondition;
  let nestedSmithyCore;
  let source;

  beforeAll(() => {
    root = fs.mkdtempSync(path.join(os.tmpdir(), 'aws-sdk-subpath-imports-'));
    source = path.join(root, 'src', 'models');
    fs.mkdirSync(source, {recursive: true});
    smithyCore = writePackage(root, '@smithy/core', {exports: SMITHY_CORE_EXPORTS});
    noBrowserCondition = writePackage(root, '@aws-sdk/example-no-browser', {
      exports: NO_BROWSER_CONDITION_EXPORTS
    });
    writePackage(root, '@aws-sdk/example-no-exports', {main: './dist-cjs/index.js'});
    const importer = writePackage(root, '@aws-sdk/example-importer', {});
    nestedSmithyCore = writePackage(importer, '@smithy/core', {
      exports: {'./serde': './dist-es/submodules/serde/index.js'}
    });
  });

  afterAll(() => {
    fs.rmSync(root, {recursive: true, force: true});
  });

  it('matches only a subpath of an @aws-sdk or @smithy package', () => {
    expect(AWS_SDK_SUBPATH_IMPORT.test('@smithy/core/serde')).toBe(true);
    expect(AWS_SDK_SUBPATH_IMPORT.test('@aws-sdk/core/client')).toBe(true);
    expect(AWS_SDK_SUBPATH_IMPORT.test('@aws-sdk/nested-clients/sso')).toBe(true);
    expect(AWS_SDK_SUBPATH_IMPORT.test('@aws-sdk/client-omics')).toBe(false);
    expect(AWS_SDK_SUBPATH_IMPORT.test('@smithy/core')).toBe(false);
    expect(AWS_SDK_SUBPATH_IMPORT.test('aws-sdk/clients/s3')).toBe(false);
    expect(AWS_SDK_SUBPATH_IMPORT.test('@babel/runtime/helpers/extends')).toBe(false);
  });

  it('prefers the browser ES module build', () => {
    expect(resolveFrom(source, '@smithy/core/serde'))
      .toBe(path.join(smithyCore, 'dist-es/submodules/serde/index.browser.js'));
  });

  it('falls back to the ES module build where there is no browser condition', () => {
    expect(resolveFrom(source, '@aws-sdk/example-no-browser/plain'))
      .toBe(path.join(noBrowserCondition, 'dist-es/submodules/plain/index.js'));
  });

  it('uses the copy that node resolution finds first from the importing file', () => {
    const importer = path.join(root, 'node_modules', '@aws-sdk', 'example-importer', 'dist-es');
    expect(resolveFrom(importer, '@smithy/core/serde'))
      .toBe(path.join(nestedSmithyCore, 'dist-es/submodules/serde/index.js'));
  });

  it('leaves a subpath that "exports" does not list alone', () => {
    const deepImport = '@smithy/core/dist-es/index.js';
    expect(resolveFrom(source, deepImport)).toBe(deepImport);
    expect(resolveFrom(source, '@smithy/core/not-exported')).toBe('@smithy/core/not-exported');
  });

  it('leaves a package without "exports", or one not installed, alone', () => {
    const noExports = '@aws-sdk/example-no-exports/dist-es/index.js';
    expect(resolveFrom(source, noExports)).toBe(noExports);
    expect(resolveFrom(source, '@smithy/missing/serde')).toBe('@smithy/missing/serde');
  });
});
