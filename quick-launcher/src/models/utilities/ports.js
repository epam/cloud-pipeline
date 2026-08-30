export function parsePorts(value) {
  if (!value) {
    return [];
  }
  return value.split(/[\s,;]+/g)
    .map(port => port.trim())
    .filter(port => port.length && !Number.isNaN(Number(port)))
    .map(port => Number(port));
}

export function displayPorts(ports = []) {
  return ports.join(', ');
}

export function portsStringValidation (value, settings) {
  if (!value || !settings || !settings.customToolEndpointsEnabled) {
    return [];
  }
  const config = typeof settings.customToolEndpointsEnabled === 'object'
    ? settings.customToolEndpointsEnabled
    : {};
  const {
    ports: portsConfig = {}
  } = config;
  let {
    count = 3,
    from = 1,
    to = Infinity
  } = portsConfig;
  from = Number(from);
  to = Number(to);
  let rangeString = `Ports should be in rage of ${from} - ${to}`;
  if (!Number.isFinite(to)) {
    rangeString = `Ports should be greater than ${from}`;
  }
  const ports = parsePorts(value);
  const errors = [];
  if (ports.length > Number(count)) {
    errors.push(`Maximum ${count} ports allowed.`);
  }
  ports.forEach((port) => {
    const outOfRange = Number(port) < from || Number(port) > to;
    if (/\s/g.test(port)) {
      errors.push(`Invalid port value`);
    } else if (outOfRange) {
      errors.push(rangeString);
    }
  });
  return errors;
}

export function getPortParameters (value) {
  if (!value || !value.length) {
    return {};
  }
  const ports = parsePorts(value);
  console.log('Specifying custom tool endpoints ports:', ports);
  return ports
    .reduce((acc, port, index) => ({
      ...acc,
      [`CP_CAP_CUSTOM_TOOL_ENDPOINT_${index + 1}_PORT`]: {
        value: `${port}`,
        type: "string"
      }
    }), {});
}

export function getRunPorts (run) {
  const {
    pipelineRunParameters = []
  } = run;
  return pipelineRunParameters
    .filter(p => /^CP_CAP_CUSTOM_TOOL_ENDPOINT_/i.test(p.name))
    .map(p => Number(p.value))
    .filter(n => !Number.isNaN(n))
    .sort((a, b) => a - b)
}
