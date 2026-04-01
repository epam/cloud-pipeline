import {isObservableArray} from 'mobx';

export function createNewEntry (properties = []) {
  const result = {};
  for (const prop of (properties || [])) {
    const {name, type, value = /^(bool|boolean)$/i.test(type) ? false : undefined} = prop;
    result[name] = {
      type,
      value
    };
  }
  return result;
}

export function checkEntryPropertyValid (entry, property) {
  const {
    name,
    required = true
  } = property || {};
  if (!name) {
    return true;
  }
  const {value} = entry[name] || {};
  return !(
    required &&
    (value === undefined || value === null || String(value).trim().length === 0)
  );
}

export function checkEntryValid (entry, properties) {
  return !properties.some((prop) => !checkEntryPropertyValid(entry, prop));
}

export function checkSchemeParameterValid (value, properties) {
  if (!value) {
    return true;
  }
  if (Array.isArray(value) || isObservableArray(value)) {
    return !value.some((entry) => !checkEntryValid(entry, properties));
  }
  return false;
}
