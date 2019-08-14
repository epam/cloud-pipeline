const names = [
  'B',
  'KB',
  'MB',
  'GB',
  'TB',
];
const base = 1024;

export default function (size) {
  let baseIndex = 0;
  let sizeRemains = size;
  while (baseIndex < names.length - 1 && sizeRemains > base) {
    sizeRemains = Math.floor(sizeRemains / base);
    baseIndex += 1;
  }
  return `${sizeRemains} ${names[baseIndex]}`;
}
