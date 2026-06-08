/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const evalSourceMapMiddleware = require('react-dev-utils/evalSourceMapMiddleware');
const noopServiceWorkerMiddleware = require('react-dev-utils/noopServiceWorkerMiddleware');
const ignoredFiles = require('react-dev-utils/ignoredFiles');
const redirectServedPath = require('react-dev-utils/redirectServedPathMiddleware');
const paths = require('./paths');
const fs = require('fs');

const protocol = process.env.HTTPS === 'true' ? 'https' : 'http';
const host = process.env.HOST || '0.0.0.0';

module.exports = function (proxy, allowedHost) {
  const disableFirewall =
    !proxy || process.env.DANGEROUSLY_DISABLE_HOST_CHECK === 'true';
  return {
    allowedHosts: disableFirewall ? 'all' : [allowedHost],
    compress: true,
    static: {
      directory: paths.appPublic,
      publicPath: [paths.servedPath],
      watch: {
        ignored: ignoredFiles(paths.appSrc)
      }
    },
    client: {
      logging: 'none',
      overlay: false,
      webSocketURL: {
        hostname: '0.0.0.0'
      }
    },
    devMiddleware: {
      publicPath: paths.servedPath.slice(0, -1)
    },
    https: protocol === 'https',
    host,
    hot: true,
    historyApiFallback: {
      disableDotRule: true,
      index: paths.servedPath
    },
    proxy,
    setupMiddlewares (middlewares, devServer) {
      if (fs.existsSync(paths.proxySetup)) {
        require(paths.proxySetup)(devServer.app);
      }

      middlewares.push(
        evalSourceMapMiddleware(devServer),
        redirectServedPath(paths.servedPath),
        noopServiceWorkerMiddleware(paths.servedPath)
      );

      return middlewares;
    }
  };
};
