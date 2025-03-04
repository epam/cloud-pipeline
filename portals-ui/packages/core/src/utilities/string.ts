const textDecoder = new TextDecoder();

export const escapeRegExpCharacters = ['.', '-', '+', '*', '?', '^', '$', '(', ')', '[', ']', '{', '}'];

export function escapeRegExp(string: string, characters = escapeRegExpCharacters): string {
  let result = string;
  characters.forEach((character) => {
    result = result.replace(new RegExp('\\' + character, 'g'), `\\${character}`);
  });
  return result;
}

export type CorrectPathOptions = {
  ensureLeadingSlash?: boolean;
  removeLeadingSlash?: boolean;
  ensureTrailingSlash?: boolean;
  removeTrailingSlash?: boolean;
};

export function correctPath(path: string | undefined, options?: CorrectPathOptions): string {
  const { ensureLeadingSlash, ensureTrailingSlash } = options ?? {};
  const {
    removeLeadingSlash = ensureLeadingSlash === undefined ? undefined : !ensureLeadingSlash,
    removeTrailingSlash = ensureTrailingSlash === undefined ? undefined : !ensureTrailingSlash,
  } = options ?? {};
  let corrected = path ?? '';
  if (!corrected.startsWith('/') && ensureLeadingSlash) {
    corrected = '/'.concat(corrected);
  }
  if (corrected.startsWith('/') && removeLeadingSlash) {
    corrected = corrected.slice(1);
  }
  if (!corrected.endsWith('/') && ensureTrailingSlash) {
    corrected = corrected.concat('/');
  }
  if (corrected.endsWith('/') && removeTrailingSlash) {
    corrected = corrected.slice(0, -1);
  }
  return corrected;
}

export function joinPath(...path: string[]): string {
  if (path.length === 0) {
    return '';
  }
  if (path.length < 2) {
    return path[0];
  }
  const [parent, current, ...rest] = path;
  const joined = `${correctPath(parent, { removeTrailingSlash: true })}/${correctPath(current, { removeLeadingSlash: true })}`;
  return joinPath(joined, ...rest);
}

export function parentPath(path: string): string {
  if (/^file:\/\//i.test(path)) {
    path = '/' + path.slice('file://'.length); // Replace file:// with / for file URLs
  }

  if (/^(https|http|ftp):\/\//i.test(path)) {
    // For URLs, respect the path and return the parent path
    const url = new URL(path);
    const parts = url.pathname.split('/').filter(Boolean);
    parts.pop(); // Remove last segment to get parent path
    url.pathname = '/' + parts.join('/');
    return url.toString().replace(/\/$/, ''); // Remove trailing slash if any
  }

  const corrected = correctPath(path, { removeTrailingSlash: true });
  return corrected.split('/').slice(0, -1).join('/');
}

export function capitalizedString(input: string | undefined): string {
  if (!input || input.length === 0) {
    return input ?? '';
  }
  return input.slice(0, 1).toUpperCase().concat(input.slice(1));
}

export function unCapitalizedString(input: string | undefined): string {
  if (!input || input.length === 0) {
    return input ?? '';
  }
  return input.slice(0, 1).toLowerCase().concat(input.slice(1));
}

function base64ToArrayBuffer(base64: string) {
  const binaryString = atob(base64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

export function base64toString(base64: string, omitWrappingQuotes?: boolean) {
  let str = base64;
  if (omitWrappingQuotes) {
    if (/^'|"/.test(str) && /'|"$/.test(str)) {
      str = str.slice(1, -1);
    }
  }
  const buffer = base64ToArrayBuffer(str);
  return textDecoder.decode(buffer);
}
