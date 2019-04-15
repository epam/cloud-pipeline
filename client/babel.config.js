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

const presets = [
  [
    '@babel/preset-env',
    {
      targets: {
        browsers: [
          'last 2 versions',
          'ie > 10'
        ]
      }
    }
  ],
  '@babel/preset-react',
  'react-app'
];
const plugins = [
  [
    'import',
    {
      libraryName: 'antd',
      style: true
    },
    'antd'
  ],
  [
    'import',
    {
      libraryName: 'ui-portal-boilerplate',
      libraryDirectory: 'components'
    },
    'ui-portal-boilerplate'
  ],
  [
    '@babel/plugin-proposal-decorators',
    {
      legacy: true
    }
  ],
  [
    '@babel/plugin-proposal-class-properties',
    {
      loose: true
    }
  ],
  [
    '@babel/plugin-transform-runtime',
    {
      regenerator: true
    }
  ],
  '@babel/plugin-external-helpers',
  '@babel/plugin-syntax-dynamic-import',
  '@babel/plugin-syntax-import-meta',
  '@babel/plugin-proposal-json-strings',
  '@babel/plugin-proposal-function-sent',
  '@babel/plugin-proposal-export-namespace-from',
  '@babel/plugin-proposal-numeric-separator',
  '@babel/plugin-proposal-throw-expressions',
  '@babel/plugin-proposal-export-default-from',
  '@babel/plugin-proposal-logical-assignment-operators',
  '@babel/plugin-proposal-optional-chaining',
  [
    '@babel/plugin-proposal-pipeline-operator',
    {
      proposal: 'minimal'
    }
  ],
  '@babel/plugin-proposal-nullish-coalescing-operator',
  '@babel/plugin-proposal-do-expressions',
  '@babel/plugin-proposal-function-bind'
];

module.exports = {presets, plugins};
