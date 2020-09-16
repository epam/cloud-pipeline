import moment from 'moment-timezone';

export default function parse (token) {
  if (!token) {
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
};
