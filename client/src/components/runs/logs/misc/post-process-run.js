export function normalizeRunParameter (runParameter) {
  const {name, type} = runParameter || {};
  if (type === undefined && name) {
    // trying to parse "unparsed" name
    const parts = name.split('=');
    if (parts.length >= 3) {
      const [paramName, ...restParts] = parts;
      const paramType = restParts.pop();
      return {
        ...runParameter,
        name: paramName,
        type: paramType,
        value: restParts.join('=')
      };
    }
  }
  return runParameter;
}

export function normalizeRunParameters (runParameters) {
  return (runParameters || []).map(normalizeRunParameter);
}

function compareRunParameters (parameter1, parameter2) {
  const {name: p1Name} = parameter1 || {};
  const {name: p2Name} = parameter2 || {};
  if (p1Name.startsWith('CP_') && !p2Name.startsWith('CP_')) {
    return 1;
  }
  if (!p1Name.startsWith('CP_') && p2Name.startsWith('CP_')) {
    return -1;
  }
  return p1Name.localeCompare(p2Name);
}

export function sortRunParameters (runParameters) {
  return (runParameters || []).slice().sort(compareRunParameters);
}
