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

export function escapeRegExp(string: string, characters = escapeRegExpCharacters): string {
  let result = string;
  characters.forEach((character) => {
    result = result.replace(new RegExp('\\' + character, 'g'), `\\${character}`);
  });
  return result;
}
