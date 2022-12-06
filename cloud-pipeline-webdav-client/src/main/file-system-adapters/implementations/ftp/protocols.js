const ftp = 'ftp';
const ftps = 'ftp-ssl implicit';
const ftpes = 'ftp-ssl explicit';
const sftp = 'sftp';

module.exports = {
  ftp,
  ftps,
  ftpes,
  sftp,
  parse(aProtocol) {
    if (/^ftp$/i.test(aProtocol)) {
      return ftp;
    }
    if (/^sftp$/i.test(aProtocol)) {
      return sftp;
    }
    if (
      /(^|\s)ftp(\s|$)/i.test(aProtocol)
      && /(^|\s)implicit(\s|$)/i.test(aProtocol)
    ) {
      return ftps;
    }
    if (
      /(^|\s)ftp(\s|$)/i.test(aProtocol)
      && /(^|\s)explicit(\s|$)/i.test(aProtocol)
    ) {
      return ftpes;
    }
    if (/^ftpes$/i.test(aProtocol)) {
      return ftpes;
    }
    if (/^ftps$/i.test(aProtocol)) {
      return ftps;
    }
    return ftp;
  },
};
