import {
  compareUserNames,
  compareUserNamesWithoutDomain,
  getUserDisplayName,
  isUser,
  isUserInfo,
  userMatchesCriteria,
} from '../../src';
import { generateUser, generateUserInfo } from '../helpers/users.ts';

describe('User and UserInfo validation tests', () => {
  test('isUser should return true for a valid User object', () => {
    const user = {
      id: 1,
      userName: 'testuser',
      roles: [],
      attributes: {},
    };
    expect(isUser(user)).toBe(true);
  });

  test('isUser should return false for an invalid User object', () => {
    const user = { id: 1, name: 'testuser' }; // Missing userName
    expect(isUser(user)).toBe(false);
  });

  test('isUserInfo should return true for a valid UserInfo object', () => {
    const userInfo = {
      id: 1,
      name: 'John Doe',
    };
    expect(isUserInfo(userInfo)).toBe(true);
  });

  test('isUserInfo should return false for an invalid UserInfo object', () => {
    const userInfo = { id: 1, userName: 'John Doe' }; // Missing 'name' field
    expect(isUserInfo(userInfo)).toBe(false);
  });
});

describe('User name comparison tests', () => {
  test('compareUserNames should return true if names are equal (case-insensitive)', () => {
    expect(compareUserNames('testUser', 'Testuser')).toBe(true);
  });

  test('compareUserNames should return false if names are different', () => {
    expect(compareUserNames('testUser', 'otherUser')).toBe(false);
  });

  test('compareUserNames should return true if both names are undefined', () => {
    expect(compareUserNames(undefined, undefined)).toBe(true);
  });

  test('compareUserNames should return false if the first user name is undefined', () => {
    expect(compareUserNames(undefined, 'otherUser')).toBe(false);
  });

  test('compareUserNames should return false if the second user name is undefined', () => {
    expect(compareUserNames('testUser', undefined)).toBe(false);
  });

  test('compareUserNamesWithoutDomain should return true if names are equal ignoring domain', () => {
    expect(compareUserNamesWithoutDomain('test.user@example.com', 'Test.User@example.com')).toBe(true);
  });

  test('compareUserNamesWithoutDomain should return false if names are different', () => {
    expect(compareUserNamesWithoutDomain('test.user@example.com', 'test.otheruser@example.com')).toBe(false);
  });

  test('compareUserNamesWithoutDomain should return true if both names are undefined', () => {
    expect(compareUserNamesWithoutDomain(undefined, undefined)).toBe(true);
  });

  test('compareUserNamesWithoutDomain should return false if the first user name is undefined', () => {
    expect(compareUserNamesWithoutDomain(undefined, 'test.otheruser@example.com')).toBe(false);
  });

  test('compareUserNamesWithoutDomain should return false if the second user name is undefined', () => {
    expect(compareUserNamesWithoutDomain('test.otheruser@example.com', undefined)).toBe(false);
  });
});

describe('getUserDisplayName tests', () => {
  test('getUserDisplayName should return full name if both first and last name exist', () => {
    const user = generateUser({
      id: 1,
      userName: 'user123',
      attributes: {
        firstname: 'John',
        lastname: 'Doe',
      },
    });
    expect(getUserDisplayName(user)).toBe('Doe John');
    expect(getUserDisplayName(generateUserInfo(user))).toBe('Doe John');
  });

  test('getUserDisplayName should return only the name if first and last name do not exist', () => {
    const user = generateUser({
      id: 2,
      userName: 'user123',
      attributes: {
        Name: 'Jane Doe',
      },
    });
    expect(getUserDisplayName(user)).toBe('Jane Doe');
    expect(getUserDisplayName(generateUserInfo(user))).toBe('Jane Doe');
  });

  test('getUserDisplayName should return only the name if first and last name are the same', () => {
    const user = generateUser({
      id: 2,
      userName: 'user123',
      attributes: {
        firstname: 'John',
        lastname: 'John',
      },
    });
    expect(getUserDisplayName(user)).toBe('John');
    expect(getUserDisplayName(generateUserInfo(user))).toBe('John');
  });

  test('getUserDisplayName should return the userName if no valid name or attributes exist', () => {
    const user1 = generateUser({
      id: 3,
      userName: 'user123',
      attributes: {},
    });
    const user2 = generateUser({
      id: 3,
      userName: 'user123',
    });
    expect(getUserDisplayName(user1)).toBe('user123');
    expect(getUserDisplayName(generateUserInfo(user1))).toBe('user123');
    expect(getUserDisplayName(user2)).toBe('user123');
    expect(getUserDisplayName(generateUserInfo(user2))).toBe('user123');
  });
});

describe('userMatchesCriteria tests', () => {
  test('userMatchesCriteria should return true when name matches the criteria', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
    });
    expect(userMatchesCriteria(user, 'testuser')).toBe(true);
    expect(userMatchesCriteria(generateUserInfo(user), 'testuser')).toBe(true);
  });

  test('userMatchesCriteria should return false when name does not match the criteria', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
    });
    expect(userMatchesCriteria(user, 'anotheruser')).toBe(false);
    expect(userMatchesCriteria(generateUserInfo(user), 'anotheruser')).toBe(false);
  });

  test('userMatchesCriteria should return true when attribute matches the criteria', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
      attributes: { email: 'testuser@example.com' },
    });
    expect(userMatchesCriteria(user, 'example')).toBe(true);
    expect(userMatchesCriteria(generateUserInfo(user), 'example')).toBe(true);
  });

  test('userMatchesCriteria should return false if no criteria match', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
      attributes: { email: 'testuser@example.com' },
    });
    expect(userMatchesCriteria(user, 'otherdomain')).toBe(false);
    expect(userMatchesCriteria(generateUserInfo(user), 'otherdomain')).toBe(false);
  });

  test('userMatchesCriteria should return true when criteria is empty or whitespace', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
      attributes: {},
    });
    expect(userMatchesCriteria(user, '')).toBe(true);
    expect(userMatchesCriteria(generateUserInfo(user), '')).toBe(true);
    expect(userMatchesCriteria(user, '   ')).toBe(true);
    expect(userMatchesCriteria(generateUserInfo(user), '   ')).toBe(true);
  });

  test('userMatchesCriteria should return false when attribute matches the criteria, but searchAttributes options is set to false', () => {
    const user = generateUser({
      id: 1,
      userName: 'testuser',
      attributes: { email: 'testuser@example.com' },
    });
    expect(userMatchesCriteria(user, 'example', false)).toBe(false);
    expect(userMatchesCriteria(generateUserInfo(user), 'example', false)).toBe(false);
  });
});
