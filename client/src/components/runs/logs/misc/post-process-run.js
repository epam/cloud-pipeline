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
