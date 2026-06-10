/// <reference types="vitest/config" />
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {defineConfig, withFilter} from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import swc from '@rollup/plugin-swc';
import {buildDevProxy, buildEnvDefines, resolveViteBase} from './config/vite-env';

/** MobX / HOC decorators at line start — excludes JSDoc (` * @param`). */
const MOBX_DECORATOR_RE = /(?:^|\n)(?!\s*\*)\s*@[\w.]+/;

/** CSS module naming: `[name]__[local]` with kebab-case basename (legacy webpack convention). */
function cssModuleBaseName(filePath: string): string {
  const base = path.basename(filePath).split('.')[0];
  return base
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
    .toLowerCase();
}

function generateScopedName(localName: string, filePath: string): string {
  return `${cssModuleBaseName(filePath)}__${localName}`;
}

const clientRoot = path.dirname(fileURLToPath(import.meta.url));
const pipelineBuilderVendor = path.join(clientRoot, 'public/pipeline-builder');

export default defineConfig(({mode}) => ({
  base: resolveViteBase(mode),
  define: buildEnvDefines(mode),
  build: {
    outDir: 'build',
    emptyOutDir: true,
    rolldownOptions: {
      output: {
        manualChunks(id) {
          if (id.includes(`${pipelineBuilderVendor}${path.sep}pipeline.min.js`)) {
            return 'pipeline-builder';
          }
        },
      },
    },
  },
  publicDir: 'public',
  resolve: {
    alias: {
      'pipeline-builder-vendor': pipelineBuilderVendor,
    },
  },
  plugins: [
    tailwindcss(),
    react(),
    withFilter(
      swc({
        include: /\/src\/.*\.[jt]sx?$/,
        swc: {
          jsc: {
            parser: {
              syntax: 'ecmascript',
              jsx: true,
              decorators: true,
            },
            transform: {
              legacyDecorator: true,
              decoratorMetadata: false,
            },
          },
        },
      }),
      {transform: {code: MOBX_DECORATOR_RE}},
    ),
  ],
  css: {
    modules: {
      localsConvention: 'camelCase',
      generateScopedName,
    },
  },
  optimizeDeps: {
    entries: ['index.html'],
    // Pure ESM — pre-bundling breaks named export (AnsiUp becomes undefined in dev)
    exclude: ['ansi_up'],
    include: ['@aws-sdk/client-omics', '@aws-sdk/client-s3'],
  },
  legacy: {
    inconsistentCjsInterop: true,
  },
  server:
    mode === 'production'
      ? undefined
      : {
          host: process.env.HOST || '0.0.0.0',
          port: Number(process.env.PORT) || 3000,
          proxy: buildDevProxy(),
        },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.{js,jsx,ts,tsx}'],
    passWithNoTests: true,
  },
}));
