import { fileURLToPath } from "url";
import path from "path";

import baseConfig from "../eslint-base.config.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";

export default [
  ...baseConfig,
  {
    files: ["**/*.ts", "**/*.tsx"],
    ignores: ["src/cp-run-view/webview/**/*.ts"],
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
      },
    },
    plugins: {
      react: reactPlugin,
      reactHooks: reactHooks,
    },
    rules: {
      "@typescript-eslint/no-floating-promises": "error",
      "@typescript-eslint/ass": "off",
    },
  },
  {
    files: ["src/cp-run-view/webview/**/*.ts"],
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.cp-run-view.json",
        tsconfigRootDir: __dirname,
      },
    },
    plugins: {
      react: reactPlugin,
      reactHooks: reactHooks,
    },
    rules: {
      "@typescript-eslint/no-floating-promises": "error",
    },
  },
  {
    files: ["webpack.config.js", "jest.config.js"],
    rules: {
      "@typescript-eslint/no-var-requires": "off",
    },
  },
];
