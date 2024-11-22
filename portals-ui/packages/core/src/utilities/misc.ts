export class AbortError extends Error {
  constructor(message?: string) {
    super(message ?? 'Aborted');
  }
}

export function logError(error: any, message?: string): void {
  if (error instanceof AbortError) {
    return;
  }
  const errorDescription =
    error instanceof Error ? error.message : (error as string);
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

export const escapeRegExpCharacters = [
  '.',
  '-',
  '+',
  '*',
  '?',
  '^',
  '$',
  '(',
  ')',
  '[',
  ']',
  '{',
  '}',
];

export function escapeRegExp(
  string: string,
  characters = escapeRegExpCharacters,
): string {
  let result = string;
  characters.forEach((character) => {
    result = result.replace(
      new RegExp('\\' + character, 'g'),
      `\\${character}`,
    );
  });
  return result;
}

export type CorrectPathOptions = {
  ensureLeadingSlash?: boolean;
  removeLeadingSlash?: boolean;
  ensureTrailingSlash?: boolean;
  removeTrailingSlash?: boolean;
};

export function correctPath(
  path: string | undefined,
  options?: CorrectPathOptions,
): string {
  const { ensureLeadingSlash = false, ensureTrailingSlash = false } =
    options ?? {};
  const {
    removeLeadingSlash = !ensureLeadingSlash,
    removeTrailingSlash = !ensureTrailingSlash,
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
  if (path.endsWith('://')) {
    return path;
  }
  const corrected = correctPath(path, { removeTrailingSlash: true });
  return corrected.split('/').slice(0, -1).join('/');
}

export function capitalizedString(input: string): string {
  if (!input || input.length === 0) {
    return input ?? '';
  }
  return input.slice(0, 1).toUpperCase().concat(input.slice(1));
}

export function unCapitalizedString(input: string): string {
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
