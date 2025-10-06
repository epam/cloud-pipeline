import {defineConfig, type UserConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default ({ mode }: UserConfig) => {
  const devMode = mode === 'development' || mode === 'dev';
  const origin = devMode ? 'http://localhost:3030' : ''
  return defineConfig({
    base: "",
    plugins: [react()],
    build: {
      rollupOptions: {
        output: {
          assetFileNames(asset) {
            const assetName = asset.name;
            if (assetName && /\.js$/i.test(assetName)) {
              return 'js/[name].[hash:10].[ext]';
            }
            if (assetName && /\.css$/i.test(assetName)) {
              return 'css/[name].[hash:10].[ext]';
            }
            if (assetName && /\.wasm$/i.test(assetName)) {
              return 'wasm/[name].[hash:10].[ext]';
            }
            return 'assets/[name].[hash:10].[ext]';
          },
          chunkFileNames: 'js/[name].[hash:10].js',
          entryFileNames: 'js/[name].[hash:10].js',
          manualChunks(id) {
            if (id.includes('node_modules')) {
              return 'vendor';
            }
            if (id.includes('hterm')) {
              return 'hterm';
            }
          },
        },
      },
      commonjsOptions: { transformMixedEsModules: true } // for ketcher to work
    },
    define: {
      global: 'window',
      WEB_SSH_TERMINAL_MODE: JSON.stringify(mode),
      WEB_SSH_ORIGIN: JSON.stringify(origin),
    },
  });
};
