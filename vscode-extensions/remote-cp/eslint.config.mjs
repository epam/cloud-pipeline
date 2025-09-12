import globals from "globals";
import js from "@eslint/js";
import tseslint from "typescript-eslint";

import jsxA11y from "eslint-plugin-jsx-a11y";
import importPlugin from "eslint-plugin-import";
import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";

import prettierPlugin from "eslint-plugin-prettier";

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
    files: ["**/*.ts", "**/*.mjs"],
    languageOptions: {
      parser: tseslint.parser,
      ecmaVersion: "latest",
      sourceType: "module",
      globals: {
        ...globals.browser,
        ...globals.node,
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
      "prettier/prettier": "warn",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_" },
      ],
    },
  },
];
