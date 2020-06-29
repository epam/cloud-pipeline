module.exports = function (data) {
  const str = data
    .toString('base64')
    .split('')
    .reduce((result, char, index) => {
      if (index % 64 === 0) {
        result.push([]);
      }
      result[result.length - 1].push(char);
      return result;
    }, [])
    .filter(r => r.length > 0)
    .map(r => r.join(''))
    .join('\n');
  return `-----BEGIN CERTIFICATE-----\n${str}\n-----END CERTIFICATE-----`;;
}
