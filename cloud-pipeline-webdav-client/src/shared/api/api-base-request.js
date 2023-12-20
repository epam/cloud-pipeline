const https = require('https');
const logger = require('../shared-logger');
const ApiError = require('./api-error');

const REQUEST_TIMEOUT_SECONDS = 10;

function getMethodURL(api = '', endpoint = '') {
  const apiCorrected = api.endsWith('/') ? api : api.concat('/');
  const endpointCorrected = endpoint.startsWith('/') ? endpoint.slice(1) : endpoint;
  return new URL(endpointCorrected, apiCorrected);
}

/**
 * @typedef {Object} ApiRequestOptions
 * @property {string} [method=GET]
 * @property {string} [token]
 * @property {string} [bearerCookie]
 * @property {number} [timeout] - timeout in seconds
 * @property {*} [body]
 * @property {boolean} [rejectUnauthorized=false]
 */

/**
 * @param {string} api
 * @param {string} endpoint
 * @param {ApiRequestOptions} [options]
 * @returns {Promise<unknown>}
 */
module.exports = function apiBaseRequest(api, endpoint, options = {}) {
  if (!api) {
    throw new Error('API url not specified');
  }
  const {
    body,
    method = (body ? 'POST' : 'GET'),
    token,
    bearerCookie,
    timeout = REQUEST_TIMEOUT_SECONDS,
    rejectUnauthorized,
  } = options;
  const headers = {
    'Content-type': 'application/json',
    Accept: 'application/json',
    'Accept-Charset': 'utf-8',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  } else if (bearerCookie) {
    headers.Cookie = `bearer=${bearerCookie}`;
  }
  const TITLE = `[API ${api}]`;
  const log = (...message) => logger.log(TITLE, ...message);
  const logError = (...message) => logger.error(TITLE, ...message);
  const url = getMethodURL(api, endpoint);
  const requestOptions = {
    method,
    headers,
    timeout: timeout * 1000,
    rejectUnauthorized,
  };
  return new Promise((resolve, reject) => {
    const request = https.request(url, requestOptions, (response) => {
      response.setEncoding('utf8');
      let data = '';
      response.on('data', (chunk) => { data += chunk; });
      response.on('end', () => {
        try {
          log(`${endpoint}: ${data.length} bytes`);
          const json = JSON.parse(data);
          if (json && /^ok$/i.test(json.status)) {
            resolve(json.payload);
          } else {
            reject(new ApiError(json.message || `Error fetching "${endpoint}"`));
            logError(`${endpoint}: ${json.message}`);
          }
        } catch (e) {
          logError(`${endpoint}:`, e.message);
          reject(e);
        }
      });
    });
    request.on('error', reject);
    request.on('timeout', () => {
      logError(`${endpoint}: timeout`);
      request.destroy();
      reject(new Error('Timeout'));
    });
    if (typeof body === 'string') {
      request.write(Buffer.from(body));
    } else if (body) {
      request.write(Buffer.from(JSON.stringify(body)));
    }
    request.end();
  });
};
