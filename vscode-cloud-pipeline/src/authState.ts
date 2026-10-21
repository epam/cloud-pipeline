/** After HTTP 401/403, treat credentials as unusable until sign-in or refresh. */
let pipeAuthInvalidated = false;

export function invalidatePipeAuth(): void {
  pipeAuthInvalidated = true;
}

export function clearPipeAuthInvalidation(): void {
  pipeAuthInvalidated = false;
}

export function isPipeAuthInvalidated(): boolean {
  return pipeAuthInvalidated;
}
