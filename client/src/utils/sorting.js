/**
 * Compares two strings in ascending alphabetical order.
 * If one of the values is not a string, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function alphabeticalSorter (a, b) {
  if (typeof a !== 'string' && typeof b !== 'string') return 0;
  if (typeof a !== 'string') return 1;
  if (typeof b !== 'string') return -1;
  return a.localeCompare(b);
}

/**
 * Compares two strings in descending alphabetical order.
 * If one of the values is not a string, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function alphabeticalSorterDesc (a, b) {
  if (typeof a !== 'string' && typeof b !== 'string') return 0;
  if (typeof a !== 'string') return 1;
  if (typeof b !== 'string') return -1;
  return b.localeCompare(a);
}

/**
 * Compares two numbers in ascending order.
 * If one of the values is not a number, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function numericalSorter (a, b) {
  if (typeof a !== 'number' && typeof b !== 'number') return 0;
  if (typeof a !== 'number') return 1;
  if (typeof b !== 'number') return -1;
  return a - b;
}

/**
 * Compares two numbers in descending order.
 * If one of the values is not a number, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function numericalSorterDesc (a, b) {
  if (typeof a !== 'number' && typeof b !== 'number') return 0;
  if (typeof a !== 'number') return 1;
  if (typeof b !== 'number') return -1;
  return b - a;
}

/**
 * Compares two boolean values in ascending order (false < true).
 * If one of the values is not a boolean, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function booleanSorter (a, b) {
  if (typeof a !== 'boolean' && typeof b !== 'boolean') return 0;
  if (typeof a !== 'boolean') return 1;
  if (typeof b !== 'boolean') return -1;
  return (a === b) ? 0 : a ? 1 : -1;
}

/**
 * Compares two boolean values in descending order (true < false).
 * If one of the values is not a boolean, it is placed at the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function booleanSorterDesc (a, b) {
  if (typeof a !== 'boolean' && typeof b !== 'boolean') return 0;
  if (typeof a !== 'boolean') return 1;
  if (typeof b !== 'boolean') return -1;
  return (a === b) ? 0 : a ? -1 : 1;
}

const typeOrder = {string: 1, number: 2, boolean: 3};

/**
 * Default sorter that handles different data types.
 * - Strings are sorted alphabetically.
 * - Numbers are sorted numerically.
 * - Booleans are treated as false < true.
 * - Different types are ordered as: string > number > boolean.
 * - Complex types (objects, arrays, functions) are always pushed to the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function defaultSorter (a, b) {
  const typeA = typeof a;
  const typeB = typeof b;

  if (typeA === typeB) {
    if (typeA === 'string') return a.localeCompare(b);
    if (typeA === 'number') return a - b;
    if (typeA === 'boolean') return (a === b) ? 0 : a ? 1 : -1;
  }

  if (typeA in typeOrder && typeB in typeOrder) {
    return typeOrder[typeA] - typeOrder[typeB];
  }

  if (typeA in typeOrder) {
    return -1;
  }

  if (typeB in typeOrder) {
    return 1;
  }

  return 0;
}

/**
 * Default sorter in descending order that handles different data types.
 * - Strings are sorted alphabetically (descending).
 * - Numbers are sorted numerically (descending).
 * - Booleans are treated as true < false.
 * - Different types are ordered as: string > number > boolean.
 * - Complex types (objects, arrays, functions) are always pushed to the end.
 * @param {*} a - First value to compare.
 * @param {*} b - Second value to compare.
 * @returns {number} Sorting order.
 */
export function defaultSorterDesc (a, b) {
  const typeA = typeof a;
  const typeB = typeof b;

  if (typeA === typeB) {
    if (typeA === 'string') return b.localeCompare(a);
    if (typeA === 'number') return b - a;
    if (typeA === 'boolean') return (a === b) ? 0 : a ? -1 : 1;
  }

  if (typeA in typeOrder && typeB in typeOrder) {
    return typeOrder[typeB] - typeOrder[typeA];
  }

  if (typeA in typeOrder) {
    return -1;
  }

  if (typeB in typeOrder) {
    return 1;
  }

  return 0;
}
