const AVG_CHAR_WIDTH = 7.5;
const PADDING = 16;

// A function made to ensure that table header is one line high
export function calculateMinColWidth(arr: string[]): number {
  const longestString = arr.reduce((max, str) => (str.length > max.length ? str : max), '');

  return AVG_CHAR_WIDTH * longestString.length + PADDING;
}
