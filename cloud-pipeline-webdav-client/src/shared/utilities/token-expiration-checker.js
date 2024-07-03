/* eslint-disable no-underscore-dangle */
const EventEmitter = require('events');
const moment = require('moment-timezone');
const displayDate = require('./display-date');

/**
 * @typedef {Object} TokenInfo
 * @property {string} sub
 * @property {moment.Moment} exp
 * @property {moment.Moment} iat
 */

/**
 * @param {string} token
 * @returns {TokenInfo|undefined}
 */
function parse(token) {
  if (!token || typeof token !== 'string') {
    return undefined;
  }
  try {
    const payload = token.split('.')[1];
    const tokenParsed = JSON.parse(atob(payload));
    tokenParsed.exp = moment(new Date(tokenParsed.exp * 1000));
    tokenParsed.iat = moment(new Date(tokenParsed.iat * 1000));
    return tokenParsed;
  } catch (_) {
    return undefined;
  }
}

class TokenExpirationChecker extends EventEmitter {
  get token() {
    return this._token;
  }

  set token(value) {
    if (value !== this._token) {
      this._token = value;
      this._tokenInfo = parse(value);
    }
  }

  get tokenInfo() {
    return this._tokenInfo;
  }

  get expired() {
    if (!this.tokenInfo) {
      return false;
    }
    return this.tokenInfo.exp < moment();
  }

  get expireInDays() {
    if (!this.tokenInfo) {
      return Infinity;
    }
    if (this.expired) {
      return 0;
    }
    return Math.floor(this.tokenInfo.exp.diff(moment(), 'days'));
  }

  checkTokenExpiration() {
    if (this.tokenInfo) {
      const payload = {
        token: this.token,
        subject: this.tokenInfo.sub,
        issuedAt: displayDate(this.tokenInfo.iat, 'D MMMM YYYY, HH:mm'),
        expiresAt: displayDate(this.tokenInfo.exp, 'D MMMM YYYY, HH:mm'),
        expired: this.expired,
        expireInDays: this.expireInDays,
      };
      if (payload.expired) {
        this.emit('token-expired', payload);
      } else if (payload.expireInDays <= 7) {
        this.emit('token-will-expire', payload);
      }
    }
  }
}

module.exports = TokenExpirationChecker;
