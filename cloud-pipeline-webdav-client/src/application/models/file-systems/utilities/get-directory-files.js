import fs from 'fs';
import path from "path";

function getDirectoryFiles(directory) {
  if (fs.existsSync(directory) && fs.lstatSync(directory).isDirectory()) {
    const children = fs.readdirSync(directory, {withFileTypes: true});
    const results = [];
    for (let i = 0; i < (children || []).length; i++) {
      const child = children[i];
      if (child.isDirectory()) {
        results.push(...getDirectoryFiles(path.resolve(directory, child.name)));
      } else {
        results.push(path.resolve(directory, child.name));
      }
    }
    return results;
  }
  return [directory];
}

export default getDirectoryFiles;
