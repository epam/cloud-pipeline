import globals from "globals";
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import importPlugin from "eslint-plugin-import";

/**
 * Shared ESLint flat config base for TypeScript projects.
 * Provides recommended rules for ES, TypeScript, and imports.
 *
 * Usage:
 *   import baseConfig from "../cp-client-common/eslint-base.config.mjs";
 *   export default [
 *     ...baseConfig,
 *     { files: ["**\/*.ts"], languageOptions: { parserOptions: { project: "./tsconfig.json", tsconfigRootDir: __dirname } } },
 *   ];
 */
export default [
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.ts", "**/*.mjs"],
    ignores: ["dist/**/*", "node_modules/**/*", "out/**/*"],
    languageOptions: {
      parser: tseslint.parser,
      globals: {
        ...globals.node,
        ...globals.browser,
      },
    },
    plugins: {
      import: importPlugin,
    },
    rules: {
      // TypeScript
      "@typescript-eslint/no-empty": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        {
          argsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
        },
      ],
      // General
      "no-useless-escape": "warn",
      "no-debugger": "warn",
    },
  },
];
