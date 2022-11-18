import {escapeRegExp} from './escape-reg-exp';
import { applyModifiers } from "../process-string";

export function pathComponentHasPlaceholder (pathComponent) {
  return /.*\[[^\[\]]+\].*/.test(pathComponent);
}

export default function PathComponent(configuration) {
  const {
    path,
    hasPlaceholders,
    gatewaySpecFile
  } = configuration;
  const groups = [];
  const regExp = /\[([^\]\[]*)\]/;
  let p = path.slice();
  let e = regExp.exec(p);
  let mask = '^';
  while (e) {
    mask = `${mask}${escapeRegExp(p.slice(0, e.index))}(.*?)`;
    p = p.slice(e.index + (e[0] || '').length);
    groups.push(e[1] || '');
    e = regExp.exec(p);
  }
  mask = `${mask}${escapeRegExp(p)}$`;
  return {
    path,
    hasPlaceholders,
    gatewaySpecFile,
    groups,
    mask: new RegExp(mask, 'i'),
    parsePathComponent (pathComponent) {
      const info = {};
      const e = this.mask.exec(pathComponent);
      if (e && e.length === this.groups.length + 1) {
        for (let g = 0; g < this.groups.length; g++) {
          const [groupName, modifiers] = this.groups[g].split(':');
          info[groupName] = applyModifiers(e[g + 1], modifiers);
        }
        return info;
      }
      return false;
    }
  };
}
