module.exports = function getFlag(args, ...flag) {
  for (const f of flag) {
    if (args.includes(f)) {
      return true;
    }
    const a = args.find((o) => o.startsWith(`${f}=`));
    if (a) {
      return a.slice(f.length + 1);
    }
  }
  return undefined;
};
