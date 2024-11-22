import type { InputOptions, OutputOptions, RollupOptions } from 'rollup';

import typescriptPlugin from '@rollup/plugin-typescript';
import terserPlugin from '@rollup/plugin-terser';
import dtsPlugin from 'rollup-plugin-dts';

export type DefaultRollupConfigOptions = {
  input: InputOptions['input'];
  output: string;
  iifeName?: string;
};

export function buildDefaultRollupConfig(
  options: DefaultRollupConfigOptions,
): RollupOptions[] {
  const { input, output, iifeName = output } = options;
  const commonInputOptions: InputOptions = {
    input,
    plugins: [typescriptPlugin()],
  };
  const iifeCommonOutputOptions: OutputOptions = {
    name: iifeName,
  };
  return [
    {
      ...commonInputOptions,
      output: [
        {
          file: `dist/${output}.esm.js`,
          format: 'esm',
        },
      ],
    },
    {
      ...commonInputOptions,
      output: [
        {
          ...iifeCommonOutputOptions,
          file: `dist/${output}.umd.js`,
          format: 'umd',
        },
        {
          ...iifeCommonOutputOptions,
          file: `dist/${output}.umd.min.js`,
          format: 'umd',
          plugins: [terserPlugin()],
        },
      ],
    },
    {
      ...commonInputOptions,
      output: [
        {
          file: `dist/${output}.cjs.js`,
          format: 'cjs',
        },
      ],
    },
    {
      ...commonInputOptions,
      plugins: [commonInputOptions.plugins, dtsPlugin()],
      output: [
        {
          file: `dist/${output}.d.ts`,
          format: 'esm',
        },
      ],
    },
  ];
}
