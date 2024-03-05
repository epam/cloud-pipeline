export default function parseRunServiceUrl (url) {
  if (!url) {
    return [];
  }
  if (typeof url === 'object' && !Array.isArray(url)) {
    const [anyConfig] = Object.values(url);
    return parseRunServiceUrl(anyConfig);
  }
  if (!url || !url.length) {
    return [];
  }
  try {
    return JSON.parse(url);
  } catch (__) {
    return url.split(';').map(part => ({
      name: null,
      url: part
    }));
  }
}
