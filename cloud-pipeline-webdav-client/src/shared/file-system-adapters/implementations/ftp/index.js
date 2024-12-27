const WebBasedAdapter = require('../web-based');
const FTPInterface = require('./interface');
const Protocols = require('./protocols');

function parseFTPUrl(url, protocol = Protocols.ftp) {
  if (!url) {
    return {};
  }
  let urlCorrected = url;
  const e = /^[^:]+:\/\/(.+)$/.exec(urlCorrected);
  if (e && e[1]) {
    [, urlCorrected] = e;
  }
  let portNumber;
  switch (protocol) {
    case Protocols.sftp:
      portNumber = 22;
      break;
    case Protocols.ftps:
      portNumber = 990;
      break;
    case Protocols.ftpes:
    case Protocols.ftp:
    default:
      portNumber = 21;
      break;
  }
  const portExec = /^(.+):(\d+)$/.exec(urlCorrected);
  if (portExec) {
    portNumber = Number(portExec[2]);
    [, urlCorrected] = portExec;
  }
  return {
    url: urlCorrected,
    port: portNumber,
    protocol,
  };
}

/**
 * @typedef {Object} FTPAdapterOptions
 * @property {string} protocol
 * @property {string} protocol
 * @property {number} port
 * @property {boolean} enableLogs
 * @property {boolean} ignoreCertificateErrors
 */

class FTPAdapter extends WebBasedAdapter {
  /**
   * @param {FileSystemAdapterOptions & FTPAdapterOptions} options
   */
  constructor(options) {
    const {
      url,
      port,
      protocol,
    } = parseFTPUrl(options?.url, options?.protocol);
    super({
      name: `FTP ${url}`,
      ...options,
      url,
      port,
    });
    this.port = port;
    this.protocol = protocol;
    this.rootName = url;
    this.enableLogs = options?.enableLogs;
    this.ignoreCertificateErrors = options?.ignoreCertificateErrors;
  }

  toString() {
    return [
      `Adapter ${this.type}:`,
      `  url:                ${this.url}`,
      `  port:               ${this.port}`,
      `  protocol:           ${this.protocol}`,
      `  user:               ${this.user || '<empty>'}`,
      `  password:           ${this.password ? '***' : '<empty>'}`,
      `  ignore cert errors: ${!!this.ignoreCertificateErrors}`,
      `  enable logs:        ${!!this.enableLogs}`,
    ].join('\n');
  }

  async createInterface() {
    const ftpInterface = new FTPInterface(this);
    await ftpInterface.initialize({
      url: this.url,
      protocol: this.protocol,
      port: this.port,
      user: this.user,
      password: this.password,
      enableLogs: this.enableLogs,
      ignoreCertificateErrors: this.ignoreCertificateErrors,
    });
    return ftpInterface;
  }

  /**
   * @param {FileSystemAdapterOptions} options
   * @returns {boolean}
   */
  equals(options) {
    return this.url === options?.url && this.port === options?.port && super.equals(options);
  }

  async useLastRequestOnlySession(sessionName, fn) {
    if (this.protocol === Protocols.sftp) {
      return this.useSession(fn);
    }
    return super.useLastRequestOnlySession(sessionName, fn);
  }
}

module.exports = FTPAdapter;
