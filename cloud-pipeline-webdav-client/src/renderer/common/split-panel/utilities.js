// eslint-disable-next-line import/prefer-default-export
export function getGridIndex(index) {
  if (typeof index === 'string') {
    return index;
  }
  if (Number.isNaN(Number(index))) {
    return 1;
  }
  return Number(index) + 1;
}
