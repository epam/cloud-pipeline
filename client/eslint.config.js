import tseslint from 'typescript-eslint';
import neostandard from 'neostandard';
import globals from 'globals';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import eslintConfigPrettier from 'eslint-config-prettier/flat';

export default [
  {ignores: ['build/**', 'node_modules/**', 'config/**', 'scripts/**']},
  ...neostandard({noJsx: false, semi: true, noStyle: true}),
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{js,jsx,ts,tsx}'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: {jsx: true},
        experimentalDecorators: true,
      },
      globals: {
        ...globals.browser,
        SERVER: 'readonly',
      },
    },
    plugins: {
      react: reactPlugin,
      'react-hooks': reactHooksPlugin,
    },
    rules: {
      '@typescript-eslint/no-restricted-imports': [
        'error',
        {
          paths: [
            {name: 'moment', message: 'Use utils/dayjs instead.'},
            {name: 'moment-timezone', message: 'Use utils/dayjs instead.'},
            {
              name: 'dayjs',
              message: 'Import from utils/dayjs so plugins are registered once.',
              allowTypeImports: true,
            },
          ],
        },
      ],
      'no-restricted-imports': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/ban-ts-comment': 'off',
      '@typescript-eslint/no-var-requires': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-empty-function': 'off',
      '@typescript-eslint/no-inferrable-types': 'off',
      '@typescript-eslint/no-this-alias': 'off',
      'no-plusplus': ['error', {allowForLoopAfterthoughts: true}],
      'react/prop-types': 0,
      'react/no-unused-prop-types': 0,
      'react/jsx-handler-names': 0,
    },
  },
  eslintConfigPrettier,
  {
    files: ['src/utils/dayjs.js'],
    rules: {
      '@typescript-eslint/no-restricted-imports': 'off',
    },
  },
];
