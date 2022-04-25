/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const TerserWebpackPlugin = require('terser-webpack-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = (env, args) => {
    const mode = args.mode || 'production';
    const development = /^development$/i.test(mode);
    let publicPath = development ? '' : (process.env.CP_MAINTENANCE_PUBLIC_URL || '/maintenance');
    if (!development && !publicPath.endsWith('/')) {
        publicPath = publicPath.concat('/');
    }
    if (!development) {
        console.log('Public url:', publicPath);
    }
    return {
        mode: development ? 'development' : 'production',
        context: path.resolve(__dirname),
        entry: {
            main: {
                import: './src/index.js',
            },
        },
        output: {
            path: path.resolve(__dirname, './build'),
            filename: 'static/js/[name].[contenthash].js',
            clean: true,
            publicPath,
            assetModuleFilename: (pathData) => {
              if (/\.(png|svg|jpg)$/i.test(pathData.filename)) {
                return 'images/[name][ext]';
              }
              if (/templates\/.+\.md$/.test(pathData.filename)) {
                return 'templates/[name][ext]';
              }
              return '[name][ext]';
            }
        },
        resolve: {
            fallback: {
                util: false,
                os: false,
                path: false,
                fs: false,
            },
        },
        devtool: development ? 'eval-source-map' : false,
        plugins: [
          new CopyWebpackPlugin({
            patterns: [
              {
                context: path.resolve(__dirname, 'public'),
                from: '**/*',
                noErrorOnMissing: true
              },
            ],
          }),
            new MiniCssExtractPlugin({
                filename: 'static/css/[name].[contenthash].css',
                chunkFilename: 'static/css/[name].[contenthash].chunk.css',
            }),
            new HtmlWebpackPlugin({
                template: path.resolve(__dirname, './src/index.html'),
                favicon: path.resolve(__dirname, './src/favicon.png'),
                minify: true,
            }),
        ],
        optimization: {
            minimize: true,
            minimizer: [
                new TerserWebpackPlugin({
                    terserOptions: {
                        parse: {
                            ecma: 8,
                        },
                        compress: true,
                        mangle: {
                            safari10: true,
                        },
                        output: {
                            ecma: 5,
                            comments: false,
                            ascii_only: true,
                        },
                        sourceMap: development,
                    },
                    parallel: true,
                }),
                new CssMinimizerPlugin({
                    exclude: /slideatlas-viewer/,
                }),
            ],
            splitChunks: {
                cacheGroups: {
                    vendor: {
                        test: /[\\/]node_modules[\\/]/,
                        name: 'vendors',
                        chunks: 'all',
                    },
                },
            },
            runtimeChunk: 'single',
        },
        module: {
            rules: [
                {
                    test: /\.(md|json)$/,
                    type: 'asset/resource'
                },
                {
                    test: /\.(png|jpg|svg)$/,
                    type: 'asset/resource'
                },
                {
                    test: /\.css$/i,
                    exclude: /node_modules/,
                    use: [
                        development ? 'style-loader' : MiniCssExtractPlugin.loader,
                        {
                            loader: 'css-loader',
                            options: {
                                importLoaders: 2,
                                sourceMap: development,
                                url: true,
                                modules: false,
                            }
                        },
                        {
                            loader: 'postcss-loader',
                            options: {
                                sourceMap: development,
                            },
                        }
                    ],
                },
                {
                    test: /\.css$/i,
                    include: /node_modules/,
                    use: [
                        development ? 'style-loader' : MiniCssExtractPlugin.loader,
                        {
                            loader: 'css-loader',
                            options: {
                                importLoaders: 2,
                                sourceMap: development,
                                url: false,
                                modules: false,
                            }
                        },
                        {
                            loader: 'postcss-loader',
                            options: {
                                sourceMap: development,
                            },
                        }
                    ]
                },
                {
                    test: /\.?js[x]?$/,
                    exclude: /node_modules/,
                    use: {
                        loader: 'babel-loader',
                        options: {
                            presets: ['@babel/preset-env'],
                            plugins: ['@babel/plugin-transform-runtime'],
                        },
                    },
                },
            ],
        },
    };
};
