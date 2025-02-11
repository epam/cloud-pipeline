export class AbortError extends Error {
  constructor(message?: string) {
    super(message ?? 'Aborted');
  }
}

export function logError(error: unknown, message?: string): void {
  if (error instanceof AbortError) {
    return;
  }
  const errorDescription = error instanceof Error ? error.message : (error as string);
  console.warn(message ? `${message}: ${errorDescription}` : errorDescription);
}

export type NumberIdentifierGenerator = () => number;
export function createNumberIdentifierGenerator(): NumberIdentifierGenerator {
  let idx = 0;
  return (): number => {
    idx += 1;
    return idx;
  };
}

export type IdentifierGenerator = () => string;
export function createIdentifierGenerator(name?: string): IdentifierGenerator {
  const numberGenerator = createNumberIdentifierGenerator();
  return () => {
    if (name) {
      return `${name}-${numberGenerator()}`;
    }
    return `${numberGenerator()}`;
  };
}

export function noop() {
  // noop
}

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

export function createSingleCallPromise<PromiseResult>(
  fn: () => Promise<PromiseResult>,
  resetOnError = false,
): () => Promise<PromiseResult> {
  let singleCallPromise: Promise<PromiseResult> | undefined;
  return async (): Promise<PromiseResult> => {
    if (!singleCallPromise) {
      singleCallPromise = fn();
      if (resetOnError) {
        singleCallPromise.catch(() => {
          singleCallPromise = undefined;
        });
      }
    }
    return singleCallPromise;
  };
}

type AsyncFilterOptions = {
  abortSignal?: AbortSignal;
  batch?: number;
};

export async function asyncFilter<T>(
  elements: T[],
  callback: (element: T, index: number, array: T[]) => boolean,
  options?: AsyncFilterOptions,
): Promise<T[]> {
  const { batch = 10000, abortSignal } = options ?? {};
  let start = 0;
  const result: T[] = [];
  return new Promise<T[]>((resolve, reject) => {
    const fn = (): void => {
      if (abortSignal?.aborted) {
        reject(new AbortError());
      }
      const end = Math.min(elements.length, start + batch);
      try {
        for (let i = start; i < end; i += 1) {
          if (abortSignal?.aborted) {
            reject(new AbortError());
          }
          if (callback(elements[i], i, elements)) {
            result.push(elements[i]);
          }
        }
      } catch (error) {
        reject(error as Error);
        return;
      }
      start = end;
      if (end < elements.length) {
        setTimeout(fn, 0);
      } else {
        resolve(result);
      }
    };
    fn();
  });
}
