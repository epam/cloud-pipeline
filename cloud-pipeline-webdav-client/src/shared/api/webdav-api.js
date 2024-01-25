const apiBaseRequest = require('./api-base-request');
const logger = require('../shared-logger');

class WebDAVApi {
  /**
   * @typedef {object} WebDAVApiOptions
   * @property {string} url
   * @property {string} password
   * @property {boolean} [ignoreCertificateErrors]
   * @property {boolean} [correctUrl]
   */

  /**
   * @param {WebDAVApiOptions|Configuration} options
   */
  constructor(options) {
    if (!options) {
      throw new Error('WebDAV API should be initialized with configuration');
    }
    /**
     * @type {WebDAVApiOptions|Configuration}
     */
    this.options = options;
  }

  /**
   * @param {string} endpoint
   * @param {ApiRequestOptions} [options]
   * @returns {Promise<unknown>}
   */
  async apiRequest(endpoint, options = {}) {
    const {
      url,
      password,
      ignoreCertificateErrors = false,
      correctUrl = true,
    } = this.options || {};
    let webdavServer = url || '';
    if (webdavServer && webdavServer.endsWith('/')) {
      webdavServer = webdavServer.slice(0, -1);
    }
    let api;
    if (webdavServer) {
      api = correctUrl
        ? webdavServer.split('/').slice(0, -1).join('/')
        : webdavServer;
    }
    const rejectUnauthorized = !ignoreCertificateErrors;
    return apiBaseRequest(
      api,
      endpoint,
      {
        bearerCookie: password,
        rejectUnauthorized,
        ...options,
      },
    );
  }

  sendPermissionsRequest(path = []) {
    if (path.length > 0) {
      return this.apiRequest('/extra/chown', {
        body: {
          path: path.map((o) => (o.startsWith('/') ? o.slice(1) : o)),
        },
      });
    }
    return Promise.resolve();
  }

  async getChecksum(path = []) {
    if (path.length > 0) {
      try {
        const pathCorrected = path.map((o) => (o.startsWith('/') ? o.slice(1) : o));
        const result = await this.apiRequest('/extra/checksum', {
          body: {
            path: pathCorrected,
            hash_alg: 'xxhash',
          },
        });
        return pathCorrected.map((aFile, idx) => ((result || [])[idx] || {})[aFile]);
      } catch (error) {
        logger.error(`Error getting checksums: ${error.message}`);
      }
    }
    return path.map(() => undefined);
  }
}

module.exports = WebDAVApi;
