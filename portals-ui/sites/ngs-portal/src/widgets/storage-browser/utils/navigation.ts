export const ROOT_PLACEHOLDER = '/';

export function correctStoragePath(path: string | undefined): string {
  return !path || path.trim() === '' ? ROOT_PLACEHOLDER : path;
}
