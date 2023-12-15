const sizePostfix = ['bytes', 'Kb', 'Mb', 'Gb', 'Tb', 'Pb', 'Eb', 'Zb', 'Yb'];

const displaySize = (size, digits = true) => {
  if (Number.isNaN(Number(size))) {
    return undefined;
  }
  let sizeValue = +size;
  let index = 0;
  while (sizeValue > 1024 && index < sizePostfix.length - 1) {
    index += 1;
    sizeValue /= 1024;
  }
  if (index === 0) {
    return `${sizeValue} ${sizePostfix[index]}`;
  }
  if (digits) {
    return `${sizeValue.toFixed(2)} ${sizePostfix[index]}`;
  }
  return `${Math.round(sizeValue)} ${sizePostfix[index]}`;
};

module.exports = displaySize;
