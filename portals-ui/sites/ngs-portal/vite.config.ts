import type { ConfigEnv, PluginOption } from 'vite';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';
import fs from 'fs';

type DevFile = {
  pathToFile?: string;
  url: RegExp;
};

function serveDevFiles(devFiles: DevFile[]): PluginOption {
  if (devFiles.length === 0) {
    return undefined;
  }
  return {
    name: 'serve-dev',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url) {
          const devFile = devFiles.find((o) => o.url.test(req.url!));
          if (devFile) {
            if (devFile.pathToFile && fs.existsSync(devFile.pathToFile)) {
              console.log(req.url, 'serving', devFile.pathToFile);
              const settingsContent = fs
                .readFileSync(devFile.pathToFile)
                .toString();
              if (/\.json$/i.test(devFile.pathToFile)) {
                res.setHeader('content-type', 'application/json');
              }
              res.writeHead(200);
              res.write(settingsContent);
              res.end();
              return;
            }
            res.writeHead(404);
            res.end();
            return;
          }
        }
        next();
      });
    },
  };
}

// https://vitejs.dev/config/
export default (cfg: ConfigEnv) => {
  const { mode } = cfg;
  const env = loadEnv(mode, process.cwd(), '');
  const base = env.PUBLIC_URL ?? '/';
  const cloudPipelineApi = env.CLOUD_PIPELINE_API ?? '/api';
  const ngsPortalVersion = env.NGS_PORTAL_VERSION ?? '';
  if (ngsPortalVersion.length > 0) {
    console.log(`building NGS portal (version ${ngsPortalVersion})`);
  } else {
    console.log('building NGS portal');
  }
  return defineConfig({
    base,
    plugins: [
      react(),
      svgr(),
      /^development$/i.test(mode)
        ? serveDevFiles([
            {
              url: /\/settings.json\/?$/i,
              pathToFile: env.SETTINGS,
            },
          ])
        : undefined,
    ],
    build: {
      rollupOptions: {
        output: {
          assetFileNames(asset) {
            const check = (regExp: RegExp): boolean =>
              asset.name ? regExp.test(asset.name) : false;
            if (check(/\.js$/i)) {
              return 'js/[name].[hash:10].[ext]';
            }
            if (check(/\.css$/i)) {
              return 'css/[name].[hash:10].[ext]';
            }
            if (check(/\.wasm$/i)) {
              return 'wasm/[name].[hash:10].[ext]';
            }
            return 'assets/[name].[hash:10].[ext]';
          },
          chunkFileNames: 'js/[name].[hash:10].js',
          entryFileNames: 'js/[name].[hash:10].js',
          manualChunks(id) {
            const r = /\/packages\/([^/]+)(\/|$)/.exec(id);
            if (r && !id.includes('node_modules/')) {
              return r[1];
            }
            if (/\/node_modules\/(@aws|@smi)/i.test(id)) {
              return 'aws.utilities';
            }
            if (id.includes('node_modules')) {
              return 'vendor';
            }
          },
        },
      },
    },
    define: {
      CLOUD_PIPELINE_API: JSON.stringify(cloudPipelineApi),
      NGS_PORTAL_VERSION: JSON.stringify(ngsPortalVersion),
    },
  });
};
