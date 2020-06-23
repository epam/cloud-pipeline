function elementTypeSorter (a, b) {
  const {isDirectory: aIsDirectory} = a;
  const {isDirectory: bIsDirectory} = b;
  return bIsDirectory - aIsDirectory;
}

function nameSorter (a, b) {
  const {name: aName = ''} = a;
  const {name: bName = ''} = b;
  return aName.toLowerCase() - bName.toLowerCase();
}

export {elementTypeSorter, nameSorter};
