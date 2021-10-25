/* eslint-disable max-len */

const IP_PATTERN = /^(?!0)(?!.*\.$)((1?\d?\d|25[0-5]|2[0-4]\d)(\.|$)){4}$/;
const PORT_PATTERN = /([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])/;

export function validate (key, value) {
  switch (key) {
    case 'ip' : {
      if (!value) {
        return {error: true, message: 'Field is required'};
      } else if (!IP_PATTERN.test(value)) {
        return {error: true, message: 'Invalid format'};
      } else {
        return {error: false};
      }
    }
    case 'port': {
      if (!value) {
        return {error: true, message: 'Field is required'};
      } else if (value && Number(value) > 0 && PORT_PATTERN.test(value)) {
        return {error: false};
      } else {
        return {error: true, message: 'Invalid format'};
      }
    }

    case 'serverName': {
      if (value && value.trim()) {
        return {error: false};
      } else {
        return {error: true, message: 'Field is required'};
      }
    }
    // validate additional port fields
    default : {
      if (!value) {
        return {error: true, message: 'Field is required'};
      } else if (value && Number(value) > 0 && PORT_PATTERN.test(value)) {
        return {error: false};
      } else {
        return {error: true, message: 'Invalid format'};
      }
    }
  }
}
export function compareTableRecords (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const {
    serverName: serverA,
    ip: ipA,
    port: portA
  } = a;
  const {
    serverName: serverB,
    ip: ipB,
    port: portB
  } = b;
  return serverA === serverB &&
      ipA === ipB &&
      portA === portB;
}

export function contentIsEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (!compareTableRecords(a[i], b[i])) {
      return false;
    }
  }
  return true;
}

export function mockedRequest (data) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      const newContent = data.map((obj) => {
        return ({
          ...obj,
          int_serverName: 'mocked_serverName1',
          int_ip: 'mocked_IP1',
          int_port: 'mocked_port1',
          key: Math.ceil(Math.random() * 100) + 1
        });
      });
      resolve(newContent);
    }, 500);
  });
}

export const mockedData = [
  {key: '1', serverName: 'serverName1', ip: 'IP', port: 'port1', int_serverName: 'int_serverName1', int_ip: 'int_IP1', int_port: 'int_port1'},
  {key: '2', serverName: 'serverName2', ip: 'IP2', port: 'port2', int_serverName: 'int_serverName2', int_ip: 'int_IP2', int_port: 'int_port2'},
  {key: '3', serverName: 'serverName3', ip: 'IP', port: 'port3', int_serverName: 'int_serverName3', int_ip: 'int_IP3', int_port: 'int_port3'},
  {key: '4', serverName: 'serverName4', ip: 'IP', port: 'port1', int_serverName: 'int_serverName1', int_ip: 'int_IP1', int_port: 'int_port1'},
  {key: '5', serverName: 'serverName5', ip: 'IP2', port: 'port2', int_serverName: 'int_serverName2', int_ip: 'int_IP2', int_port: 'int_port2'},
  {key: '6', serverName: 'serverName6', ip: 'IP', port: 'port3', int_serverName: 'int_serverName3', int_ip: 'int_IP3', int_port: 'int_port3'}
];

export const mockedColumns = {
  external: [
    {name: 'serverName', prettyName: 'server name'},
    {name: 'ip', prettyName: 'ip'},
    {name: 'port', prettyName: 'port'}],
  internal:  [
    {name: 'int_serverName', prettyName: 'server name'},
    {name: 'int_ip', prettyName: 'ip'},
    {name: 'int_port', prettyName: 'port'}
  ]
};
