import { fileURLToPath } from "url";
import path from "path";

import globals from "globals";
import js from "@eslint/js";
import tseslint from "typescript-eslint";

import prettierPlugin from "eslint-plugin-prettier";
import importPlugin from "eslint-plugin-import";
import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
  js.configs.recommended,            // "eslint:recommended"
  ...tseslint.configs.recommended,   // "plugin:@typescript-eslint/recommended"
  // reactPlugin.configs.recommended,   // "plugin:react/recommended"
  // reactHooks.configs.recommended,    // "plugin:react-hooks/recommended"
  // jsxA11y.configs.recommended,       // "plugin:jsx-a11y/recommended"
  // importPlugin.configs.recommended,  // "plugin:import/recommended"
  // importPlugin.configs.typescript,   // "plugin:import/typescript"
  // prettier.configs.recommended,      // "plugin:prettier/recommended"

  {
    files: ["**/*.ts", "**/*.tsx", "**/*.mjs"],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
        ecmaVersion: "latest",
        sourceType: "module",
        // extraFileExtensions: [".mjs", ".tsx"],
        globals: {
          ...globals.browser,
          ...globals.node,
        },
      },
    },
    plugins: {
      prettier: prettierPlugin,
      import: importPlugin,
      react: reactPlugin,
      reactHooks: reactHooks,
    },
    rules: {
      "@typescript-eslint/no-empty": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-floating-promises": "error",
      "prettier/prettier": "warn",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_" },
      ],
      "no-useless-escape": "warn",
      "no-debugger": "warn",
    },
  },
  {
    files: ["webpack.config.js", "jest.config.js"],
    env: {
      node: true,
      commonjs: true,
    },
    plugins: {
      prettier: prettierPlugin,
      import: importPlugin,
    },
    rules: {
      "@typescript-eslint/no-var-requires": "off",
    },
  },
];
