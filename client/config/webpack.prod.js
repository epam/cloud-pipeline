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

const args = require('minimist')(process.argv.slice(2));
const isDevServerStartedAsProd = args.prod;

const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');
const ManifestPlugin = require('webpack-manifest-plugin');
const InterpolateHtmlPlugin = require('react-dev-utils/InterpolateHtmlPlugin');
const paths = require('./paths');
const getClientEnvironment = require('./env');
const webpackConfig = require('./webpack.common');
const getLocalIdent = require('./getLocalIdent');

const publicPath = isDevServerStartedAsProd ? '/' : paths.servedPath;
const publicUrl = publicPath.slice(0, -1);
const env = getClientEnvironment(publicUrl);

if (env.stringified['process.env'].NODE_ENV !== '"production"') {
  throw new Error('Production builds must have NODE_ENV=production.');
}

const cssFilename = 'static/css/[name].[contenthash:8].css';

module.exports = Object.assign({}, webpackConfig, {
  bail: true,
  devtool: 'source-map',
  output: Object.assign({}, webpackConfig.output, {
    filename: 'static/js/[name].[chunkhash:8].js',
    chunkFilename: 'static/js/[name].[chunkhash:8].chunk.js',
    publicPath
  }),
  module: {
    rules: [
      ...webpackConfig.module.rules,
      {
        test: /\.(js|jsx)$/,
        include: [paths.appSrc],
        loader: 'babel-loader'
      },
      {
        test: /\.css$/,
        use: ExtractTextPlugin.extract({
          fallback: "style-loader",
          use: [
            {
              loader: 'css-loader',
              options: {
                importLoaders: 1
              }
            },
            {loader: 'postcss-loader'}
          ]
        }),
        include: /(node_modules|src\/staticStyles)/
      },
      {
        test: /\.css$/,
        use: ExtractTextPlugin.extract({
          fallback: "style-loader",
          use: [
            {
              loader: 'css-loader',
              options: {
                importLoaders: 1,
                modules: true,
                camelCase: true,
                localIdentName: '[name]__[local]',
                getLocalIdent
              }
            },
            {loader: 'postcss-loader'}
          ]
        }),
        exclude: /(node_modules|src\/staticStyles)/
      },
      {
        test: /\.less$/,
        use: ExtractTextPlugin.extract({
          fallback: "style-loader",
          use: [
            {loader: 'css-loader'},
            {
              loader: 'less-loader',
              options: {
                modifyVars: {
                  '@icon-url': '"' + publicUrl + '/iconfont/iconfont"',
                  '@flags-root': '"' + publicUrl + '/awsregions"'
                }
              }
            }
          ]
        })
      }
    ]
  },
  plugins: [
    ...webpackConfig.plugins,
    new webpack.DefinePlugin(Object.assign({}, env.stringified, {
      'process.env.SERVER': JSON.stringify(process.env.PUBLIC_URL)
    })),
    new InterpolateHtmlPlugin(env.raw),
    new HtmlWebpackPlugin({
      inject: true,
      template: paths.appHtml,
      minify: {
        removeComments: true,
        collapseWhitespace: true,
        removeRedundantAttributes: true,
        useShortDoctype: true,
        removeEmptyAttributes: true,
        removeStyleLinkTypeAttributes: true,
        keepClosingSlash: true,
        minifyJS: true,
        minifyCSS: true,
        minifyURLs: true
      }
    }),
    new webpack.optimize.OccurrenceOrderPlugin(),
    // TEMPORARY DISABLED

    // new webpack.optimize.UglifyJsPlugin({
    //   compress: {
    //     screw_ie8: true, // React doesn't support IE8
    //     warnings: false
    //   },
    //   mangle: {
    //     screw_ie8: true
    //   },
    //   output: {
    //     comments: false,
    //     screw_ie8: true
    //   }
    // }),
    new ExtractTextPlugin(cssFilename),
    new ManifestPlugin({
      fileName: 'asset-manifest.json'
    })
  ]
});
