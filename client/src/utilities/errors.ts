export class AbortError extends Error {
  constructor(message?: string) {
    super(message ?? 'Aborted');
  }
}

export function getErrorDescription(error: any): string {
  return error instanceof Error ? error.message : typeof error === 'string' ? error : `${error}`;
}

export function logError(error: any, message?: string): void {
  if (error instanceof AbortError) {
    return;
  }
  const errorDescription = getErrorDescription(error);
  console.warn(message ? `${message}: ${errorDescription}` : errorDescription);
}
