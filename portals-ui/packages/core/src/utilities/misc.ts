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
