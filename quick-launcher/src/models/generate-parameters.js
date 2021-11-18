import processString from './process-string';

export function generateParameters(parameters, options) {
  if (parameters) {
    const result = {};
    const paramKeys = Object.keys(parameters);
    for (let k = 0; k < paramKeys.length; k++) {
      const parameter = paramKeys[k];
      result[parameter] = {
        type: 'string',
        value: processString(parameters[parameter], options),
      };
    }
    return result;
  }
  return {};
}

export function attachParameters(initial, attach) {
  if (!initial) {
    return attach;
  }
  if (!attach) {
    return initial;
  }
  const keys = Object.keys(attach);
  for (let k = 0; k < keys.length; k++) {
    const key = keys[k];
    console.log(`attaching "${key}" parameter with value: "${attach[key].value}"`);
    initial[key] = attach[key];
  }
  return initial;
}
