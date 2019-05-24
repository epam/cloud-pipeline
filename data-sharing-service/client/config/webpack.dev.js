/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CaseSensitivePathsPlugin = require('case-sensitive-paths-webpack-plugin');
const InterpolateHtmlPlugin = require('react-dev-utils/InterpolateHtmlPlugin');
const WatchMissingNodeModulesPlugin = require('react-dev-utils/WatchMissingNodeModulesPlugin');
const getClientEnvironment = require('./env');
const paths = require('./paths');
const webpackConfig = require('./webpack.common');
const getLocalIdent = require('./getLocalIdent');

const publicPath = '/';
const publicUrl = '';
const env = getClientEnvironment(publicUrl);

module.exports = Object.assign({}, webpackConfig, {
  devtool: 'eval',
  entry: [
    require.resolve('react-dev-utils/webpackHotDevClient'),
    ...webpackConfig.entry
  ],
  output: Object.assign({}, webpackConfig.output, {
    pathinfo: true,
    filename: 'static/js/bundle.js',
    publicPath
  }),
  module: {
    rules: [
      ...webpackConfig.module.rules,
      {
        test: /\.(js|jsx)$/,
        include: [paths.appSrc],
        loader: 'babel-loader',
        options: {
          cacheDirectory: true
        }
      },
      {
        test: /\.css$/,
        use: [
          {loader: 'style-loader'},
          {
            loader: 'css-loader',
            options: {
              importLoaders: 1
            }
          },
          {loader: 'postcss-loader'}
        ],
        include: /(node_modules|src\/staticStyles)/
      },
      {
        test: /\.css$/,
        use: [
          {loader: 'style-loader'},
          {
            loader: 'css-loader',
            query: {
              importLoaders: 1,
              modules: true,
              camelCase: true,
              localIdentName: '[name]__[local]',
              getLocalIdent
            }
          },
          {loader: 'postcss-loader'}
        ],
        exclude: /(node_modules|src\/staticStyles)/
      },
      {
        test: /\.less$/,
        use: [
          {loader: 'style-loader'},
          {
            loader: 'css-loader'
          },
          {loader: 'less-loader',
            options: {
              modifyVars: {'@icon-url': '"/iconfont/iconfont"'}
            }
          }
        ]
      }
    ]
  },
  plugins: [
    ...webpackConfig.plugins,
    new webpack.DefinePlugin(Object.assign({}, env.stringified, {
      'process.env.SERVER': process.env.SERVER ? JSON.stringify(process.env.SERVER) : JSON.stringify(''),
      'process.env.PUBLIC_URL': JSON.stringify('')
    })),
    new InterpolateHtmlPlugin(env.raw),
    new HtmlWebpackPlugin({
      inject: true,
      template: paths.appHtml,
    }),
    new webpack.HotModuleReplacementPlugin(),
    new CaseSensitivePathsPlugin(),
    new WatchMissingNodeModulesPlugin(paths.appNodeModules)
  ]
});
