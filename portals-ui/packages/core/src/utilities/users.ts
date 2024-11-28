import { User, UserInfo } from '../model';

const NAME_KEY_VARIANTS = ['Name', 'userName', 'name'];

const FIRST_NAME_KEY_VARIANTS = [
  'firstname',
  'first_name',
  'Firstname',
  'FirstName',
  'firstName',
];

const LAST_NAME_KEY_VARIANTS = [
  'lastname',
  'last_name',
  'Lastname',
  'LastName',
  'lastName',
];

const compareUserNames = (nameA: string, nameB: string): boolean => {
  if (!nameA || !nameB) {
    return false;
  }
  return nameA.toLowerCase() === nameB.toLowerCase();
};

const compareUserNamesWithoutDomain = (nameA: string, nameB: string) => {
  const [aLowerCased] = nameA.toLowerCase().split('@');
  const [bLowerCased] = nameB.toLowerCase().split('@');
  return aLowerCased === bLowerCased;
};

const guessUserAttributeValue = (
  variants: string[],
  user: User | UserInfo,
): string | undefined => {
  if (!user?.attributes) {
    return undefined;
  }
  return variants.reduce((acc, key) => {
    if (user.attributes[key] && typeof user.attributes[key] === 'string') {
      acc = user.attributes[key];
    }
    return acc;
  });
};

const getUserDisplayName = (user: User | UserInfo): string | undefined => {
  let name;
  if ('name' in user && typeof user.name === 'string') {
    name = user.name;
  }
  if ('userName' in user && typeof user.userName === 'string') {
    name = user.userName.toLowerCase();
  }
  const firstName = guessUserAttributeValue(FIRST_NAME_KEY_VARIANTS, user);
  const lastName = guessUserAttributeValue(LAST_NAME_KEY_VARIANTS, user);
  const nameAttribute = guessUserAttributeValue(NAME_KEY_VARIANTS, user);
  if (firstName && lastName) {
    return firstName === lastName ? firstName : `${lastName} ${firstName}`;
  }
  if (nameAttribute && typeof nameAttribute === 'string') {
    return nameAttribute;
  }
  return name;
};

export { compareUserNames, compareUserNamesWithoutDomain, getUserDisplayName };
