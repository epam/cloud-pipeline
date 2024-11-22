import { AclEntry } from '../model/acl.ts';

export function readAllowed(entry: AclEntry): boolean {
  return (entry.mask & 1) == 1;
}
