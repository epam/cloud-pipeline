import { fileURLToPath } from "url";
import path from "path";

import baseConfig from "../cp-client-common/eslint-base.config.mjs";
import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
  ...baseConfig,
  {
    files: ["**/*.ts", "**/*.tsx", "**/*.mjs"],
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
        // extraFileExtensions: [".mjs", ".tsx"],
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
