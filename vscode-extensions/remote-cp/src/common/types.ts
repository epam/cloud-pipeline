export function mirrorKeys<T extends readonly string[]>(
  keys: T,
): { [K in T[number]]: K } {
  return Object.fromEntries(keys.map((k) => [k, k])) as { [K in T[number]]: K };
}
