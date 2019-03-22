/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export default function ({ie, edge, chrome, firefox, safari, other}) {
  const browser = getBrowser();
  switch (browser.name) {
    case Browsers.ie.name: return ie;
    case Browsers.edge.name: return edge;
    case Browsers.safari.name: return safari;
    case Browsers.chrome.name: return chrome;
    case Browsers.firefox.name: return firefox;
    default: return other;
  }
}

const Browsers = {
  ie: {
    version: 11,
    name: 'IE'
  },
  edge: {
    version: 17,
    name: 'Edge'
  },
  safari: {
    version: 10,
    name: 'Safari'
  },
  chrome: {
    version: 65,
    name: 'Chrome'
  },
  firefox: {
    version: 62,
    name: 'Firefox'
  }
};

export function getBrowser () {
  const info = getBrowserInfo();
  if ((info.name || '').toLowerCase() === 'msie') {
    info.name = 'IE';
  }
  return info;
}

function getBrowserInfo () {
  const ua = navigator.userAgent;
  let tem;
  let M = ua.match(/(opera|chrome|safari|firefox|msie|edge|trident(?=\/))\/?\s*(\d+)/i) || [];
  if (/edge/i.test(ua)) {
    tem = /(edge)\/?\s*(\d+)/i.exec(ua);
    return {
      name: tem[1],
      version: (tem[2])
    };
  }
  if (/trident/i.test(M[1])) {
    tem=/\brv[ :]+(\d+)/g.exec(ua) || [];
    return {
      name: 'IE',
      version: (tem[1]||'')
    };
  }
  if (M[1]==='Chrome') {
    tem = ua.match(/\bOPR|Edge\/(\d+)/);
    if (tem !== null) {
      return {
        name: 'Opera',
        version: tem[1]
      };
    }
  }
  M = M[2]
    ? [M[1], M[2]]
    : [navigator.appName, navigator.appVersion, '-?'];
  if ((tem = ua.match(/version\/(\d+)/i)) !== null) {
    M.splice(1, 1, tem[1]);
  }
  return {
    name: M[0],
    version: M[1]
  };
}
