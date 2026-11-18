const path = require('path');
const fs = require('fs');
const HtmlWebPackPlugin = require("html-webpack-plugin");
const InterpolateHtmlPlugin = require("react-dev-utils/InterpolateHtmlPlugin");
const CopyPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const webpack = require('webpack');
const env = require('./utilities/envs');
const {isProductionMode, isDevelopmentMode} = require('./utilities/mode');
const getLastCommitSHA = require('./utilities/get-last-commit-sha');
const getCommitMessage = require('./utilities/get-commit-message');

const BUILD_VERSION = env.BUILD_VERSION || getLastCommitSHA();
const BUILD_DESCRIPTION = env.BUILD_DESCRIPTION || getCommitMessage(BUILD_VERSION);

function valueOrDefault (value, defaultValue) {
  if (value === undefined) {
    return defaultValue;
  }
  return value;
}

let publicPath = env.PUBLIC_URL || '/';
if (!publicPath.endsWith('/')) {
  publicPath = publicPath.concat('/');
}
const publicUrl = publicPath.substr(0, publicPath.length - 1);
const cpApplicationsAPI = env.CP_APPLICATIONS_API;
const isCPAPI = ['1', 'true'].indexOf(`${valueOrDefault(env.USE_CLOUD_PIPELINE_API, 1)}`) >= 0;
const darkMode = env.DARK_MODE === undefined || ['1', 'true'].indexOf(`${env.DARK_MODE}`) >= 0;
const pollingInterval = env.POLLING_INTERVAL || 1000;
const initialPollingDelay = env.INITIAL_POLLING_DELAY || 5000;
const limitMounts = env.LIMIT_MOUNTS || 'default';

console.log(env);

const globalVariables = {
  'PUBLIC_URL': publicUrl,
  'CP_APPLICATIONS_API': cpApplicationsAPI,
  'CP_APPLICATIONS_TITLE': env.CP_APPLICATIONS_TITLE || 'Applications',
  'CP_APPLICATIONS_FAVICON': env.FAVICON,
  'CP_APPLICATIONS_BACKGROUND': env.BACKGROUND
    ? ((publicUrl || '') + '/' + env.BACKGROUND).replace('//', '/')
    : undefined,
  'CP_APPLICATIONS_LOGO': env.LOGO
    ? ((publicUrl || '') + '/' + env.LOGO).replace('//', '/')
    : undefined,
  'DARK_MODE': darkMode,
  'CPAPI': isCPAPI,
  'CP_APPLICATIONS_TAG': env.CP_APPLICATIONS_TAG || 'app_type',
  'TOOLS': valueOrDefault(env.USE_CLOUD_PIPELINE_TOOLS, 1),
  'INITIAL_POLLING_DELAY': initialPollingDelay,
  'POLLING_INTERVAL': pollingInterval,
  'LIMIT_MOUNTS': limitMounts,
  'CP_APPLICATIONS_SUPPORT_NAME': env.CP_APPLICATIONS_SUPPORT_NAME,
  'USE_PARENT_NODE_ID': [1, true, 'true', '1'].indexOf(env.USE_PARENT_NODE_ID) >= 0,
  'SHOW_TIMER': [1, true, 'true', '1', undefined].indexOf(env.SHOW_TIMER) >= 0,
  'PRETTY_URL_DOMAIN': env.PRETTY_URL_DOMAIN,
  'PRETTY_URL_PATH': env.PRETTY_URL_PATH,
  'BUILD_VERSION': BUILD_VERSION,
  'BUILD_DESCRIPTION': BUILD_DESCRIPTION,
};

if (BUILD_DESCRIPTION) {
  console.log('Application version is set to', BUILD_VERSION, `(${BUILD_DESCRIPTION})`);
} else {
  console.log('Application version is set to', BUILD_VERSION);
}
console.log('Application will be hosted at:', globalVariables.PUBLIC_URL || '/');

if (globalVariables.CPAPI) {
  console.log('Cloud Pipeline API will be used for fetching and launching applications');
  console.log(`Tools with "${globalVariables.CP_APPLICATIONS_TAG}"="<host name>" attribute will be used as applications`);
  if (globalVariables.TOOLS) {
    console.log('Cloud Pipeline Tools will be used as applications');
  }
  console.log('Run polling initial delay:', globalVariables.INITIAL_POLLING_DELAY, 'ms');
  console.log('Run polling interval:', globalVariables.POLLING_INTERVAL, 'ms');
  console.log('Limit mounts:', globalVariables.LIMIT_MOUNTS);
  if (globalVariables.PRETTY_URL_DOMAIN) {
    console.log('Pretty Url Domain:', globalVariables.PRETTY_URL_DOMAIN);
  }
  if (globalVariables.PRETTY_URL_PATH) {
    console.log('Pretty Url Path:', globalVariables.PRETTY_URL_PATH);
  }
}

if (globalVariables.CP_APPLICATIONS_SUPPORT_NAME) {
  console.log('Support name:', globalVariables.CP_APPLICATIONS_SUPPORT_NAME);
}

if (globalVariables.CP_APPLICATIONS_API) {
  console.log('API endpoint:', globalVariables.CP_APPLICATIONS_API);
}

console.log('Application title:', globalVariables.CP_APPLICATIONS_TITLE);

const buildDirectory = path.resolve(__dirname, 'build');

module.exports = {
  entry: './src/index.js',
  output: {
    path: buildDirectory,
    filename: 'js/[name].bundle.js',
    publicPath,
    environment: {
      // The environment supports arrow functions ('() => { ... }').
      arrowFunction: false,
      // The environment supports BigInt as literal (123n).
      bigIntLiteral: false,
      // The environment supports const and let for variable declarations.
      const: false,
      // The environment supports destructuring ('{ a, b } = obj').
      destructuring: false,
      // The environment supports an async import() function to import EcmaScript modules.
      dynamicImport: false,
      // The environment supports 'for of' iteration ('for (const x of array) { ... }').
      forOf: false,
      // The environment supports ECMAScript Module syntax to import ECMAScript modules (import ... from '...').
      module: false
    }
  },
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader'
        }
      },
      {
        test: /\.html$/,
        use: [
          {
            loader: 'html-loader'
          }
        ]
      },
      {
        test: /\.css$/i,
        use: [MiniCssExtractPlugin.loader, 'css-loader']
      },
      {
        test: /\.(png|gif|jpeg|jpg)$/i,
        loader: 'file-loader',
        options: {
          name: 'assets/[contenthash].[ext]',
        },
      }
    ]
  },
  plugins: [
    new MiniCssExtractPlugin({
      filename: 'css/bundle.css'
    }),
    new CopyPlugin({
      patterns: [
        {from: path.resolve(__dirname, 'assets'), to: path.resolve(buildDirectory, 'assets')}
      ]
    }),
    new HtmlWebPackPlugin({
      template: './src/index.html',
      filename: './index.html',
      favicon: globalVariables.CP_APPLICATIONS_FAVICON,
      inject: true,
      publicPath,
      minify: isProductionMode ? {
        removeComments: true,
        collapseWhitespace: true,
        removeRedundantAttributes: true,
        useShortDoctype: true,
        removeEmptyAttributes: true,
        removeStyleLinkTypeAttributes: true,
        keepClosingSlash: true,
        minifyJS: true,
        minifyCSS: true
      } : undefined
    }),
    new webpack.DefinePlugin(
      Object.keys(globalVariables)
        .map(key => ({[key]: JSON.stringify(globalVariables[key])}))
        .reduce((r, c) => ({...r, ...c}), {})
    ),
    new InterpolateHtmlPlugin(
      HtmlWebPackPlugin,
      globalVariables
    )
  ],
  optimization: {
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          chunks: 'all',
        },
      },
    },
  },
  devServer: isDevelopmentMode
    ? {
      historyApiFallback: {
        index: publicPath
      },
      port: env.PORT || 8080
    }
    : undefined,
};
