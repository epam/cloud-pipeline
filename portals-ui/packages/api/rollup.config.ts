import { buildDefaultRollupConfig } from './rollup.utilities';

export default buildDefaultRollupConfig({
  input: 'src/index.ts',
  output: 'api',
  iifeName: 'cloudpipelineapi',
});
