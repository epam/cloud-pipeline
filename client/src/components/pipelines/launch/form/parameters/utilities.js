/**
 * @param {Parameter} parameter
 * @returns string
 */
export function getParameterKeyClassName(parameter) {
  const {key} = parameter || {};
  return `launch-form-parameter-key-${key}`;
}
