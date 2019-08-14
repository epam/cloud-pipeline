const presets = [
  [
    '@babel/preset-env',
    {
      targets: {
        browsers: [
          'last 2 versions',
          'ie > 10',
        ],
      },
    },
  ],
  '@babel/preset-react',
  'react-app',
];
const plugins = [
  [
    'import',
    {
      libraryName: 'antd',
      style: true,
    },
    'antd',
  ],
  [
    '@babel/plugin-proposal-decorators',
    {
      legacy: true,
    },
  ],
  [
    '@babel/plugin-proposal-class-properties',
    {
      loose: true,
    },
  ],
  [
    '@babel/plugin-transform-runtime',
    {
      regenerator: true,
    },
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
      proposal: 'minimal',
    },
  ],
  '@babel/plugin-proposal-nullish-coalescing-operator',
  '@babel/plugin-proposal-do-expressions',
  '@babel/plugin-proposal-function-bind',
];

module.exports = {presets, plugins};
