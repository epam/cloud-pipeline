import { User, UserInfo } from '../model';

const NAME_KEY_VARIANTS = ['Name', 'userName', 'name'];

const FIRST_NAME_KEY_VARIANTS = ['firstname', 'first_name', 'Firstname', 'FirstName', 'firstName'];

const LAST_NAME_KEY_VARIANTS = ['lastname', 'last_name', 'Lastname', 'LastName', 'lastName'];

export function isUser(o: unknown): o is User {
  return (
    o !== undefined &&
    o !== null &&
    typeof o === 'object' &&
    'id' in o &&
    typeof o.id === 'number' &&
    'userName' in o &&
    typeof o.userName === 'string' &&
    (!('attributes' in o) || typeof o.attributes === 'object')
  );
}

export function isUserInfo(o: unknown): o is UserInfo {
  return (
    o !== undefined &&
    o !== null &&
    typeof o === 'object' &&
    'id' in o &&
    typeof o.id === 'number' &&
    'name' in o &&
    typeof o.name === 'string' &&
    (!('attributes' in o) || typeof o.attributes === 'object')
  );
}

export function compareUserNames(nameA: string | undefined, nameB: string | undefined): boolean {
  if (!nameA && !nameB) {
    return true;
  }
  if (!nameA || !nameB) {
    return false;
  }
  return nameA.toLowerCase() === nameB.toLowerCase();
}

export function compareUserNamesWithoutDomain(nameA: string | undefined, nameB: string | undefined): boolean {
  if (!nameA && !nameB) {
    return true;
  }
  if (!nameA || !nameB) {
    return false;
  }
  const [aLowerCased] = nameA.toLowerCase().split('@');
  const [bLowerCased] = nameB.toLowerCase().split('@');
  return aLowerCased === bLowerCased;
}

function guessUserAttributeStringValue(variants: string[], user: User | UserInfo): string | undefined {
  const { attributes } = user;
  if (!attributes) {
    return undefined;
  }
  for (const variant of variants) {
    if (variant in attributes && typeof attributes[variant] === 'string') {
      return attributes[variant];
    }
  }
  return undefined;
}

export const getUserDisplayName = (user: User | UserInfo): string | undefined => {
  const name = isUserInfo(user) ? user.name : user.userName;
  const firstName = guessUserAttributeStringValue(FIRST_NAME_KEY_VARIANTS, user);
  const lastName = guessUserAttributeStringValue(LAST_NAME_KEY_VARIANTS, user);
  const nameAttribute = guessUserAttributeStringValue(NAME_KEY_VARIANTS, user);
  if (firstName && lastName) {
    return firstName === lastName ? firstName : `${lastName} ${firstName}`;
  }
  if (nameAttribute && typeof nameAttribute === 'string') {
    return nameAttribute;
  }
  return name;
};

export function userMatchesCriteria(
  user: User | UserInfo,
  criteria: string | undefined,
  searchAttributes = true,
): boolean {
  if (!criteria || criteria.trim().length === 0) {
    return true;
  }
  const criteriaLowerCased = criteria.trim().toLowerCase();
  const name = (isUserInfo(user) ? user.name : user.userName).trim().toLowerCase();
  if (name.includes(criteriaLowerCased)) {
    return true;
  }
  if (searchAttributes) {
    const attributes: string[] = Object.values(user.attributes ?? {})
      .map((o) => o.toString().trim().toLowerCase())
      .filter((o) => o.length > 0);
    return attributes.some((attribute) => attribute.includes(criteriaLowerCased));
  }
  return false;
}
