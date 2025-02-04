export function removeQuotes(input: string): string {
  if (/^(".*"|'.*')$/i.test(input)) {
    return input.slice(1, -1);
  }
  return input;
}

/**
 * Compares two string arrays
 * @param array1
 * @param array2
 * @param strict - if true, checks the elements order as well
 */
export function stringArraysAreEqual(array1: string[], array2: string[], strict = false): boolean {
  if (array1.length !== array2.length) {
    return false;
  }
  const a1 = strict ? array1.slice() : array1.slice().sort();
  const a2 = strict ? array2.slice() : array2.slice().sort();
  for (let i = 0; i < a1.length; i += 1) {
    if (a1[i] !== a2[i]) {
      return false;
    }
  }
  return true;
}