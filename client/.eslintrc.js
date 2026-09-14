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

'use strict';

// JavaScript rather than JSON only so that the `indent` override below can reuse
// eslint-config-standard's own options instead of restating them - see the comment there.

const standard = require('eslint-config-standard');

// `standard` and `standard-react` both have an opinion on how JSX is indented: core `indent`
// measures it as a call argument, `react/jsx-indent` measures it by JSX nesting depth, and where
// the two bases differ no indentation satisfies both. `eslint --fix` then oscillates between them
// and silently gives up, which is why JSX indentation used to be unfixable in this project.
//
// So JSX belongs to exactly one of them: the react plugin, which understands it. Core `indent`
// keeps everything that is not JSX.
const JSX_NODES = [
  'JSXElement',
  'JSXElement > *',
  'JSXAttribute',
  'JSXIdentifier',
  'JSXMemberExpression',
  'JSXNamespacedName',
  'JSXSpreadAttribute',
  'JSXExpressionContainer',
  'JSXOpeningElement',
  'JSXClosingElement',
  'JSXText',
  'JSXEmptyExpression',
  'JSXSpreadChild'
];

// Rule options are positional, so passing a third argument replaces standard's whole options
// object. Spreading its own value in is what keeps the two from drifting apart on an upgrade.
const indentOptions = Object.assign({}, standard.rules.indent[2], {ignoredNodes: JSX_NODES});

module.exports = {
  parser: 'babel-eslint',
  extends: ['standard', 'standard-react'],
  parserOptions: {
    ecmaFeatures: {
      legacyDecorators: true
    }
  },
  env: {
    browser: true
  },
  globals: {
    SERVER: false
  },
  rules: {
    'jsx-quotes': ['error', 'prefer-double'],
    'no-plusplus': ['error', {allowForLoopAfterthoughts: true}],
    'object-curly-spacing': ['error', 'never'],
    'max-len': ['error', 100],
    'react/prop-types': 0,
    'react/no-unused-prop-types': 0,
    semi: ['error', 'always'],
    indent: ['error', standard.rules.indent[1], indentOptions]
  },
  overrides: [
    {
      files: [
        'test/**/*.js',
        'src/**/*.test.js',
        'src/**/*.test.jsx',
        'scripts/**/*.test.js'
      ],
      env: {
        jest: true
      },
      rules: {
        'import/no-unresolved': ['error', {ignore: ['^@test$']}]
      }
    }
  ]
};
