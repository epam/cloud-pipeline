export function removeQuotes(input: string): string {
  if (/^(".*"|'.*')$/i.test(input)) {
    return input.slice(1, -1);
  }
  return input;
}
