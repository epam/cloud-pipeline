const MODIFIERS = {
  'uppercased': (value) => (value || '').toUpperCase(),
  'uppercase': (value) => (value || '').toUpperCase(),
  'upper': (value) => (value || '').toUpperCase(),
  'lowercased': (value) => (value || '').toLowerCase(),
  'lowercase': (value) => (value || '').toLowerCase(),
  'lower': (value) => (value || '').toLowerCase(),
  'before@': (value) => (value || '').split('@')[0]
};

function extractModifiers(modifiers) {
  if (typeof modifiers === 'string') {
    return modifiers.split(/[;,]/g)
      .map(o => o.trim().toLowerCase());
  }
  if (modifiers && Array.isArray(modifiers)) {
    return modifiers.map(o => o.trim().toLowerCase());
  }
  return [];
}

export function applyModifiers(value, modifiers) {
  if (!value) {
    return value;
  }
  const mods = extractModifiers(modifiers);
  let resultedValue = value.slice();
  for (const modifier of mods) {
    const fn = MODIFIERS[modifier];
    if (typeof fn === 'function') {
      resultedValue = fn(resultedValue);
    }
  }
  return resultedValue;
}

export default function processString(string, options) {
  let result = '';
  const r = /(\[([^\]]+)\])/;
  let e;
  const placeholders = Object.keys(options || {})
    .map(key => ({ key: key.toLowerCase(), value: options[key] }));
  do {
    e = r.exec(string);
    if (e) {
      result = result.concat(string.slice(0, e.index));
      const [keyRaw, mods] = e[2].split(':');
      const key = keyRaw.toLowerCase();
      const placeholder = placeholders.find(p => p.key === key);
      if (placeholder) {
        const value = applyModifiers(placeholder.value, mods);
        result = result.concat(value);
      } else {
        result = result.concat(e[0]);
      }
      string = string.slice(e.index + e[0].length);
    } else {
      result = result.concat(string);
    }
  } while (e);
  return result;
}
