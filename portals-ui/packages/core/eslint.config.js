import globals from 'globals';
import pluginJs from '@eslint/js';
import tsEslint from 'typescript-eslint';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';

/** @type {import('eslint').Linter.Config[]} */
export default [
  { files: ['src/**/*.{js,mjs,cjs,ts}'] },
  { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  {
    ignores: ['dist/*', 'eslint.config.js'],
  },
  pluginJs.configs.recommended,
  ...tsEslint.configs.recommended,
  {
    rules: {
      'padding-line-between-statements': 'off',
      '@typescript-eslint/padding-line-between-statements': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    }
  },
  eslintPluginPrettierRecommended,
];
