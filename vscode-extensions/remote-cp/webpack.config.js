"use strict";

const path = require("path");
const webpack = require("webpack");
const CopyWebpackPlugin = require("copy-webpack-plugin");

const codiconSrcPathA = [__dirname, ...'node_modules/@vscode/codicons/dist'.split('/')] 
const codiconDstPathA = [__dirname, ...'dist/webview/codicon'.split('/')]

/** @returns {import('webpack').Configuration} */
function createExtensionConfig(isProduction) {
  return {
    target: "node",
    entry: {
      extension: "./src/extension.ts",
    },
    output: {
      path: path.resolve(__dirname, "dist"),
      filename: "[name].js",
      libraryTarget: "commonjs2",
      devtoolModuleFilenameTemplate: "../[resource-path]",
    },
    devtool: isProduction ? false : "source-map",
    externals: {
      vscode: "commonjs vscode",
      bufferutil: "bufferutil",
      "utf-8-validate": "utf-8-validate",
    },
    resolve: {
      extensions: [".ts", ".js"],
      fallback: {
        "@aws-sdk/client-s3": false,
      },
    },
    module: {
      rules: [
        {
          test: /\.ts$/,
          exclude: /node_modules/,
          use: [
            {
              loader: "ts-loader",
              options: {
                transpileOnly: true,
              },
            },
          ],
        },
      ],
    },
    plugins: [
      new webpack.DefinePlugin({
        navigator: "undefined",
      }),
      new webpack.IgnorePlugin({
        resourceRegExp: /crypto\/build\/Release\/sshcrypto\.node$/,
      }),
      new webpack.IgnorePlugin({
        resourceRegExp: /cpu-features/,
      }),
    ],
  };
}

/** @returns {import('webpack').Configuration} */
function createWebviewConfig(isProduction) {
  return {
    target: "web",
    entry: {
      "cp-run-view": "./src/cp-run-view/webview/main.ts",
    },
    output: {
      path: path.resolve(__dirname, "dist", "webview"),
      filename: "[name].js",
      chunkFilename: "[name].js",
      publicPath: "",
    },
    devtool: isProduction ? false : "source-map",
    resolve: {
      extensions: [".ts", ".js"],
    },
    module: {
      rules: [
        {
          test: /\.ts$/,
          exclude: /node_modules/,
          use: [
            {
              loader: "ts-loader",
              options: {
                configFile: path.resolve(__dirname, "tsconfig.cp-run-view.json"),
              },
            },
          ],
        },
      ],
    },
    plugins: [
      new CopyWebpackPlugin({
        patterns: [
          {
            from: path.resolve(...codiconSrcPathA, "codicon.css"),
            to: path.resolve(...codiconDstPathA),
          }, 
          {
            from: path.resolve(...codiconSrcPathA, "codicon.ttf"),
            to: path.resolve(...codiconDstPathA),
          }
        ],
      }),
    ],
  };
}

module.exports = (_env, argv) => {
  const mode = argv?.mode ?? "development";
  const isProduction = mode === "production";
  return [createExtensionConfig(isProduction), createWebviewConfig(isProduction)];
};
