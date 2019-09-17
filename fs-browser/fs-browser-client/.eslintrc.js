const os = require('os');

module.exports = {
  parser: 'babel-eslint',
  extends: ['airbnb'],
  env: {
    browser: true,
  },
  globals: {
    SERVER: false,
  },
  parserOptions: {
    ecmaFeatures: {
      legacyDecorators: true
    },
  },
  rules: {
    'no-underscore-dangle': ['warn', { allowAfterThis: true }],
    'no-param-reassign': 0,
    'jsx-quotes': ['error', 'prefer-double'],
    'no-plusplus': ['error', { allowForLoopAfterthoughts: true }],
    'object-curly-spacing': ['error', 'never'],
    'max-len': ['error', 120, 2, {
      ignoreUrls: true,
      ignoreComments: true,
      ignoreRegExpLiterals: true,
      ignoreStrings: true,
      ignoreTemplateLiterals: true,
    }],
    'prefer-destructuring': ['error', {
      VariableDeclarator: {
        array: false,
        object: true,
      },
      AssignmentExpression: {
        array: false,
        object: false,
      },
    }, {
      enforceForRenamedProperties: false,
    }],
    'no-unused-expressions': ['error', {
      allowShortCircuit: true,
      allowTernary: true,
    }],
    'react/jsx-one-expression-per-line': 0,
    'react/jsx-filename-extension': [1, { extensions: ['.js'] }],
    'react/prop-types': 0,
    'react/no-unused-prop-types': 0,
    semi: ['error', 'always'],
  },
};
