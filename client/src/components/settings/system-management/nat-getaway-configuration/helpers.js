const validationConfig = {
  port: {
    max: 5000,
    message: rangeMessage
  },
  ip: new RegExp(/^(?!0)(?!.*\.$)((1?\d?\d|25[0-5]|2[0-4]\d)(\.|$)){4}$/),
  server: {
    min: 5,
    max: 15,
    message: rangeMessage
  },
  messages: {
    required: 'Field is required',
    invalid: 'Invalid format',
    duplicate: 'Value should be unique'
  }
};

function rangeMessage (min, max, isStringLength = false) {
  if (min && !max) {
    return isStringLength
      ? `The length should be at least ${min} symbols`
      : `The value should be at least ${min}`;
  }
  if (max && !min) {
    return isStringLength
      ? `The length should be less than ${max}`
      : `The value should be less than ${max}`;
  }
  if (max && min) {
    return isStringLength
      ? `The length should be at least ${min} symbols and less than ${max}`
      : `The value should be between ${min} and ${max}`;
  }
}

function validatePort (value, config, ...rest) {
  const [portsDuplicates] = rest;
  const {port, messages} = config;

  const portIsValid = value && Number(value) > 0 &&
  (port.min ? Number(value) >= port.min : true) &&
  (port.max ? Number(value) <= port.max : true);

  if (!value) {
    return {error: true, message: messages.required};
  }
  if (!portsDuplicates || !portsDuplicates[value]) {
    if (Number(value) > 0 && port.min && !port.max) {
      return Number(value) >= port.min
        ? {error: false}
        : {error: true, message: port.message(port.min, port.max)};
    } else if (Number(value) > 0 && port.max && !port.min) {
      return Number(value) <= port.max
        ? {error: false}
        : {error: true, message: port.message(port.min, port.max)};
    } else if (!portIsValid) {
      return {
        error: true,
        message: (port.min || port.max)
          ? port.message(port.min, port.max)
          : messages.invalid};
    } else {
      return {error: false};
    }
  } else if (portsDuplicates && portsDuplicates[value]) {
    return {error: true, message: messages.duplicate};
  }
}

function validateServerName (value, config) {
  const {server, messages} = config;
  const serverIsValid = value && value.trim() &&
  (server.min ? value.length >= server.min : true) &&
  (server.max ? value.length <= server.max : true);

  if (!value || !value.trim()) {
    return {error: true, message: messages.required};
  } else if (server.min && !server.max) {
    return server.min <= value.length
      ? {error: false}
      : {error: true, message: server.message(server.min, server.max, true)};
  } else if (!server.min && server.max) {
    return server.max >= value.length
      ? {error: false}
      : {error: true, message: server.message(server.min, server.max, true)};
  } else if (!serverIsValid) {
    return {
      error: true,
      message: (server.min || server.max)
        ? server.message(server.min, server.max, true)
        : messages.invalid
    };
  } else {
    return {error: false};
  }
}

function validateIP (value, config) {
  const {ip, messages} = config;
  if (!value) {
    return {error: true, message: messages.required};
  } else if (!ip.test(value)) {
    return {error: true, message: messages.invalid};
  } else {
    return {error: false};
  }
}

export function validate (key, value, ...rest) {
  switch (key) {
    case 'ip' :
      return validateIP(value, validationConfig);
    case 'port':
      return validatePort(value, validationConfig, ...rest);
    case 'serverName':
      return validateServerName(value, validationConfig);
    // validate additional port fields
    default : {
      return validatePort(value, validationConfig, ...rest);
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
    externalName: extserverA,
    extrenalIp: extipA,
    externalPort: extportA,
    internalName: intserverA,
    internalIp: intipA,
    internalPort: intportA,
    isRemoved: isRemovedA
  } = a;
  const {
    externalName: extserverB,
    extrenalIp: extipB,
    externalPort: extportB,
    internalName: intserverB,
    internalIp: intipB,
    internalPort: intportB,
    isRemoved: isRemovedB
  } = b;
  return extserverA === extserverB &&
      extipA === extipB &&
      extportA === extportB &&
      intserverA === intserverB &&
      intipA === intipB &&
      intportA === intportB &&
      isRemovedA === isRemovedB;
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

export const columns = {
  external: [
    {name: 'externalName', prettyName: 'server name'},
    {name: 'externalIp', prettyName: 'ip'},
    {name: 'externalPort', prettyName: 'port'}],
  internal: [
    {name: 'internalName', prettyName: 'server name'},
    {name: 'internalIp', prettyName: 'ip'},
    {name: 'internalPort', prettyName: 'port'}
  ]
};
