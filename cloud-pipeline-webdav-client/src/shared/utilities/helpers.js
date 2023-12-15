const isEmpty = (value) => value === undefined || value === null;
const wrapSecure = (value) => (isEmpty(value) ? '<empty>' : '***');
const wrapValue = (value) => (isEmpty(value) ? '<empty>' : value);

module.exports = {
  isEmpty,
  wrapSecure,
  wrapValue,
};
