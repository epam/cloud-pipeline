import { executeAllowed, ownerAllowed, readAllowed, writeAllowed } from '../../src';

describe('Permission Checking Functions', () => {
  test('readAllowed should return true if read permission is set', () => {
    expect(readAllowed(1)).toBe(true);
    expect(readAllowed(3)).toBe(true);
    expect(readAllowed(5)).toBe(true);
    expect(readAllowed(0)).toBe(false);
    expect(readAllowed(2)).toBe(false);
  });

  test('writeAllowed should return true if write permission is set', () => {
    expect(writeAllowed(2)).toBe(true);
    expect(writeAllowed(3)).toBe(true);
    expect(writeAllowed(6)).toBe(true);
    expect(writeAllowed(0)).toBe(false);
    expect(writeAllowed(1)).toBe(false);
  });

  test('executeAllowed should return true if execute permission is set', () => {
    expect(executeAllowed(4)).toBe(true);
    expect(executeAllowed(6)).toBe(true);
    expect(executeAllowed(13)).toBe(true);
    expect(executeAllowed(0)).toBe(false);
    expect(executeAllowed(2)).toBe(false);
  });

  test('ownerAllowed should return true if owner permission is set', () => {
    expect(ownerAllowed(8)).toBe(true);
    expect(ownerAllowed(10)).toBe(true);
    expect(ownerAllowed(12)).toBe(true);
    expect(ownerAllowed(0)).toBe(false);
    expect(ownerAllowed(4)).toBe(false);
  });
});
