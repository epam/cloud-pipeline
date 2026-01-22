import { fileURLToPath } from "url";
import path from "path";

import baseConfig from "../cp-client-common/eslint-base.config.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
  ...baseConfig,
  {
    files: ["**/*.ts", "**/*.mjs"],
    ignores: ["dist/**/*", "node_modules/**/*"],
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      "newline-per-chained-call": "off",
    },
  },
];
