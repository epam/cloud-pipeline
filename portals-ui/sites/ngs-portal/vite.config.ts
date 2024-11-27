import type { ConfigEnv } from 'vite';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';

// https://vitejs.dev/config/
export default (cfg: ConfigEnv) => {
  const { mode } = cfg;
  const env = loadEnv(mode, process.cwd(), '');
  const base = env.PUBLIC_URL ?? '/';
  const cloudPipelineApi = env.CLOUD_PIPELINE_API ?? '/restapi';
  return defineConfig({
    base,
    plugins: [react(), svgr()],
    define: {
      CLOUD_PIPELINE_API: JSON.stringify(cloudPipelineApi),
    },
  });
};
