import {UserInfo} from '../../../@types/users.ts';

function getAttribute(
  attributes: UserInfo['attributes'],
  ...attribute: string[]
): string | undefined {
  const variants = attribute
    .map((attr) => [
      attr,
      attr.toLowerCase(),
      attr.toUpperCase(),
      attr.slice(0, 1).toUpperCase().concat(attr.slice(1)),
      attr.slice(0, 1).toLowerCase().concat(attr.slice(1)),
    ])
    .reduce((r, c) => [...r, ...c], []);
  const result = variants
    .map((variant) => (attributes || {})[variant])
    .find((value) => value !== undefined);
  return result ? result.toString() : undefined;
}

export function getUserDisplayName(user: UserInfo): string {
  const {attributes = {}, userName} = user;
  const firstName = getAttribute(attributes, 'FirstName', 'First Name');
  const lastName = getAttribute(attributes, 'LastName', 'Last Name');
  const attrName = getAttribute(attributes, 'name');
  if (firstName && lastName && firstName !== lastName) {
    return `${lastName} ${firstName}`;
  }
  if (firstName && lastName) {
    return firstName;
  }
  if (attrName) {
    return attrName;
  }
  return (userName || '').toLowerCase();
}
