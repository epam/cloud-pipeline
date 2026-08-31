import {defineConfig, type UserConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default ({ mode }: UserConfig) => {
  return defineConfig({
    base: "/ssh",
    server: {
      proxy: {
        // Match everything *except* /ssh/
        // Vite doesn't directly support negative matches, so we list what we forward.
        // Example: forward /api, /auth, etc.
        "^/ssh/socket.io/.*": {
          target: "http://localhost:3030/ssh/socket.io/",
          changeOrigin: true,
          secure: false,
          ws: true,
        },
      },
    },
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
      commonjsOptions: { transformMixedEsModules: true }
    },
    define: {
      global: 'window',
      WEB_SSH_TERMINAL_MODE: JSON.stringify(mode),
      WEB_SSH_ORIGIN: JSON.stringify(''),
    },
  });
};
