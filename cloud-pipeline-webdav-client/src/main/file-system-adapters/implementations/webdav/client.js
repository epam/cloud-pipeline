/* eslint-disable no-unused-vars */
const https = require('https');
const { createClient, WebDAVClient } = require('webdav');
const { PassThrough } = require('stream');
const moment = require('moment-timezone');
const WebBasedClient = require('../web-based/client');
const correctDirectoryPath = require('../../utils/correct-path');
const { getReadableStream } = require('../../utils/streams');
const WebDAVError = require('./webdav-error');
const makeDelayedLog = require('../../utils/delayed-log');
const displayDate = require('../../../common/display-date');

const DISCLAIMER = 'Typically, this means that you don\'t have any data storages available for remote access. Please contact the platform support to create them for you';

const PROGRESS_ENABLED = false;

function toBase64(string) {
  return Buffer.from(string).toString('base64');
}

class WebDAVClientImpl extends WebBasedClient {
  constructor(name) {
    super();
    this.name = name || 'Cloud Data';
  }

  async initialize(url, user, password, ignoreCertificateErrors) {
    await super.initialize(url);
    this.httpsAgent = new https.Agent({ rejectUnauthorized: !ignoreCertificateErrors });
    this.url = url;
    const urlParsed = new URL(url);
    this.logTitle = `WebDAV ${urlParsed.hostname}`;
    this.headers = {
      Authorization: `Basic ${toBase64(`${user}:${password}`)}`,
    };
    /**
     * @type {WebDAVClient}
     */
    this.client = createClient(
      url,
      {
        username: user,
        password,
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        httpsAgent: this.httpsAgent,
      },
    );
  }

  async getDirectoryContents(directory) {
    const isRoot = /^(|\/)$/.test(directory);
    try {
      this.log(`Fetching "${directory}"...`);
      const contents = await this.client.getDirectoryContents(directory);
      this.info(`Fetching "${directory}":`, contents.length, 'elements');
      /**
       * @param {*} anItem
       * @returns {FSItem}
       */
      const mapFilteredItem = (anItem) => ({
        name: anItem.basename,
        path: anItem.filename,
        isDirectory: /^directory$/i.test(anItem.type),
        isFile: /^file/i.test(anItem.type),
        isSymbolicLink: false,
        size: /^directory$/i.test(anItem.type) ? undefined : Number(anItem.size),
        changed: moment(anItem.lastmod),
        isObjectStorage: false,
        removable: !isRoot,
      });
      const results = contents.map(mapFilteredItem);
      this.logDirectoryContents(results);
      return results;
    } catch (error) {
      throw new WebDAVError(
        error,
        isRoot ? this.name : directory,
        isRoot && error.status === 404 ? DISCLAIMER : undefined,
      );
    }
  }

  async createDirectory(directory) {
    try {
      this.log(`Creating directory "${directory}"...`);
      await this.client.createDirectory(directory, { recursive: true });
      this.info(`Creating directory "${directory}" done`);
    } catch (error) {
      throw new WebDAVError(error, directory);
    }
  }

  // eslint-disable-next-line no-unused-vars
  async createReadStream(file, options = {}) {
    try {
      return this.client.createReadStream(file);
    } catch (error) {
      throw new WebDAVError(error, file);
    }
  }

  async createWriteStream(file, options = {}) {
    try {
      const {
        size,
        overwrite = true,
      } = options;
      const headers = {
        'Content-Type': 'application/octet-stream',
      };
      if (size && !Number.isNaN(Number(size))) {
        headers['Content-Length'] = Number(size);
      }
      const result = new PassThrough();
      const writeStream = this.client.createWriteStream(file, { overwrite, headers });
      result.pipe(writeStream, { end: false });
      return writeStream;
    } catch (error) {
      throw new WebDAVError(error, file);
    }
  }

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFile(element, data, options = {}) {
    return this.writeFileUsingPlainRequest(element, data, options);
  }

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFileUsingWebDAVClient(element, data, options = {}) {
    try {
      const {
        size,
        overwrite = true,
        progressCallback,
      } = options;
      const readable = getReadableStream(data);
      if (PROGRESS_ENABLED && typeof progressCallback === 'function' && size) {
        let transferred = 0;
        readable
          .on('data', (chunk) => {
            transferred += chunk.length;
            progressCallback(transferred, size);
          });
      }
      const result = await this.client.putFileContents(
        element,
        readable,
        { contentLength: size, overwrite },
      );
      if (!result) {
        throw new Error(`Error writing "${element}"`);
      }
    } catch (error) {
      throw new WebDAVError(error, element);
    }
  }

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFileUsingPlainRequest(element, data, options = {}) {
    const {
      size,
      overwrite = true,
      progressCallback,
      isAborted,
    } = options;
    const headers = {
      ...this.headers,
      Accept: 'text/plain,application/xml',
      'Content-Type': 'application/octet-stream',
    };
    if (!overwrite) {
      headers['If-None-Match'] = '*';
    }
    let remoteUrl = this.url;
    if (remoteUrl.endsWith('/')) {
      remoteUrl = remoteUrl.slice(0, -1);
    }
    if (!element.startsWith('/')) {
      remoteUrl = remoteUrl.concat('/');
    }
    remoteUrl = remoteUrl.concat(element);
    const readable = getReadableStream(data);
    const { flush, log } = makeDelayedLog(this.log.bind(this));
    const promise = new Promise((resolve, reject) => {
      const clientRequest = https.request(
        remoteUrl,
        {
          method: 'PUT',
          agent: this.httpsAgent,
          headers,
        },
        (response) => {
          response.on('data', () => {});
          response.on('end', () => {
            if (response.statusCode >= 400) {
              const error = {
                status: response.statusCode,
                statusText: response.statusMessage,
              };
              reject(new WebDAVError(error, element));
            } else {
              resolve();
            }
          });
          response.on('error', reject);
        },
      );
      clientRequest.on('error', reject);
      if (typeof progressCallback === 'function' && size) {
        let transferred = 0;
        readable
          .on('data', (chunk) => {
            transferred += chunk.length;
            log(`uploading ${element}: ${transferred} bytes sent`);
            progressCallback(transferred, size);
          });
      }
      readable.pipe(clientRequest);
      if (isAborted) {
        isAborted
          .then(() => {
            clientRequest.destroy();
          })
          .then(() => {
            readable.destroy();
          })
          .catch(() => {})
          .then(() => reject(new Error('Aborted')));
      }
    });
    promise
      .catch(() => {})
      .then(() => flush());
    return promise;
  }

  async statistics(element) {
    try {
      this.log(`Fetching ${element} statistics:`);
      const {
        size,
        lastmod,
        type,
      } = await this.client.stat(element);
      this.log(`  size        : ${size}`);
      this.log(`  modify time : ${lastmod ? displayDate(moment(lastmod)) : '<empty>'}`);
      this.log(`  type        : ${type}`);
      return {
        size,
        changed: lastmod ? moment(lastmod) : undefined,
        type,
      };
    } catch (error) {
      throw new WebDAVError(error, element);
    }
  }

  async removeDirectory(directory) {
    try {
      if (!directory) {
        return Promise.resolve();
      }
      this.info(`Removing directory "${directory}"`);
      return this.client.deleteFile(correctDirectoryPath(directory));
    } catch (error) {
      throw new WebDAVError(error, directory);
    }
  }

  async removeFile(file) {
    try {
      if (!file) {
        return Promise.resolve();
      }
      let filePathCorrected = file;
      if (filePathCorrected.endsWith('/')) {
        filePathCorrected = filePathCorrected.slice(0, -1);
      }
      this.info(`Removing file "${filePathCorrected}"`);
      return this.client.deleteFile(filePathCorrected);
    } catch (error) {
      throw new WebDAVError(error, file);
    }
  }

  async exists(element = '') {
    try {
      return this.client.exists(element);
    } catch (error) {
      throw new WebDAVError(error, element);
    }
  }

  async isDirectory(directory = '') {
    const { type } = await this.statistics(directory);
    return /^directory$/i.test(type);
  }

  async destroy() {
    if (this.client) {
      this.client = undefined;
    }
  }
}

module.exports = WebDAVClientImpl;
