export function asNumberArray(o: unknown): number[] {
  if (o === undefined || o === null || typeof o !== 'object' || !Array.isArray(o)) {
    return [];
  }
  return o
    .filter((v) => v !== undefined && v !== null && v !== '')
    .map((v) => Number(v))
    .filter((v) => !Number.isNaN(v) && Number.isFinite(v));
}

export function asStringArray(o: unknown): string[] {
  if (o === undefined || o === null || typeof o !== 'object' || !Array.isArray(o)) {
    return [];
  }
  return o.filter((v) => v !== undefined && v !== null).map((v) => v.toString());
}
