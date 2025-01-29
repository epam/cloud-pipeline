export function flatten<T>(array: undefined | null | T | T[]): T[] {
  if (array === undefined || array === null) {
    return [];
  }
  if (typeof array === 'object' && Array.isArray(array)) {
    return array;
  }
  return [array];
}

export function flattenNumberIdentifiers(
  identifiers: undefined | null | string | number | string[] | number[],
): number[] {
  if (identifiers === undefined || identifiers === null) {
    return [];
  }
  if (typeof identifiers === 'string') {
    return flattenNumberIdentifiers(
      identifiers
        .split(/[,;\s]/)
        .map((o) => o.trim())
        .filter((o) => o.length > 0),
    );
  }
  if (typeof identifiers === 'number') {
    return [identifiers];
  }
  return identifiers
    .map((id) => {
      if (typeof id === 'number') {
        return id;
      }
      if (!Number.isNaN(Number(id))) {
        return Number(id);
      }
      return undefined;
    })
    .filter((o) => o !== undefined);
}
