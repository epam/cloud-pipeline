module.exports = function getParameters(args = []) {
  return args.filter((a) => !/^--?/.test(a));
}
