export function parse(search) {
  return search
    ? search
      .slice(1)
      .split('&')
      .map((s) => {
        const [key, value] = s.split('=');
        return {key, value};
      })
      .reduce((obj, curr) => {
        obj[curr.key] = decodeURIComponent(curr.value);
        return obj;
      }, {})
    : {};
}

export function build(obj) {
  const parts = Object.entries(obj)
    .map(([name, value]) => (value ? `${name}=${value}` : null))
    .filter(Boolean);
  if (parts.length > 0) {
    return `?${parts.join('&')}`;
  }
  return '';
}
