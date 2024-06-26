import moment from 'moment-timezone';

const cookies = (document.cookie || '')
  .split(';')
  .map(o => o.trim())
  .map(o => {
    const [key, value] = o.split('=');
    return {key, value};
  });

const [tokenCookie] = cookies.filter(c => /^bearer$/i.test(c.key));
const token = tokenCookie ? tokenCookie.value : undefined;

function parseToken() {
  if (!token || typeof token !== 'string') {
    return undefined;
  }
  try {
    const payload = token.split('.')[1];
    const tokenParsed = JSON.parse(atob(payload));
    tokenParsed.exp = moment(new Date(tokenParsed.exp * 1000));
    tokenParsed.iat = moment(new Date(tokenParsed.iat * 1000));
    if (tokenParsed.exp < moment()) {
      // expired
      return undefined;
    }
    return tokenParsed;
  } catch (_) {
    return undefined;
  }
}

const tokenInfo = parseToken();

const userInfoFromToken = (() => {
  if (tokenInfo) {
    return {
      userName: tokenInfo.sub,
      roles: (tokenInfo.roles || []).map(name => ({name}))
    };
  }
  return undefined;
})();

if (tokenInfo) {
  console.log('BEARER TOKEN INFO:');
  console.log(tokenInfo);
  console.log('MOCKED USER FROM BEARER TOKEN:', userInfoFromToken);
}

export {
  token,
  tokenInfo,
  userInfoFromToken,
};
