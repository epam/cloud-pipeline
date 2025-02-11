import globals from 'globals';
import pluginJs from '@eslint/js';
import tsEslint from 'typescript-eslint';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';
import eslintPluginJest from "eslint-plugin-jest";

/** @type {import('eslint').Linter.Config[]} */
export default [
  {
    name: 'cloud-pipeline-eslint/default',
    files: ['src/**/*..{js,mjs,cjs,ts}'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
  {
    name: 'cloud-pipeline-eslint/tests',
    files: ['tests/**/*.{test,spec}.{js,mjs,cjs,ts}'],
    plugins: {
      jest: eslintPluginJest,
    },
    ...eslintPluginJest.configs['flat/recommended'],
  },
  {
    ignores: ['dist/*', 'coverage/*', 'eslint.config.js'],
  },
  {
    rules: {
      'padding-line-between-statements': 'off',
      '@typescript-eslint/padding-line-between-statements': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    }
  },
  pluginJs.configs.recommended,
  ...tsEslint.configs.recommended,
  eslintPluginPrettierRecommended,
];
