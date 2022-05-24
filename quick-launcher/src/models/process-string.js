const modifiers = {
  'uppercased': (value) => (value || '').toUpperCase(),
  'uppercase': (value) => (value || '').toUpperCase(),
  'upper': (value) => (value || '').toUpperCase(),
  'lowercased': (value) => (value || '').toLowerCase(),
  'lowercase': (value) => (value || '').toLowerCase(),
  'lower': (value) => (value || '').toLowerCase(),
  'before@': (value) => (value || '').split('@')[0]
};

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
        const appliedModifiers = (mods || '')
          .split(/[,;]/g)
          .map(mod => mod.trim().toLowerCase())
        let value = placeholder.value;
        for (const modifier of appliedModifiers) {
          const fn = modifiers[modifier];
          if (typeof fn === 'function') {
            value = fn(value);
          }
        }
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
