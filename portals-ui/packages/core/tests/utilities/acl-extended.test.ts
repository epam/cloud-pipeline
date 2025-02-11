import {
  readAllowedExtended,
  readInheritedExtended,
  readDeniedExtended,
  writeAllowedExtended,
  writeInheritedExtended,
  writeDeniedExtended,
  executeAllowedExtended,
  executeInheritedExtended,
  executeDeniedExtended,
  isAllPermissionsInheritedExtended,
} from '../../src';

describe('Permission functions', () => {
  // Test for readAllowedExtended
  test('should return true when read permission is allowed', () => {
    const mask = 1; // READ_ALLOWED
    expect(readAllowedExtended(mask)).toBe(true);
  });

  test('should return false when read permission is not allowed', () => {
    const mask = 2; // READ_DENIED
    expect(readAllowedExtended(mask)).toBe(false);
  });

  // Test for readInheritedExtended
  test('should return true when read permission is inherited (neither allowed nor denied)', () => {
    const mask = 0; // No READ permission set
    expect(readInheritedExtended(mask)).toBe(true);
  });

  test('should return false when read permission is explicitly allowed or denied', () => {
    const mask = 1; // READ_ALLOWED
    expect(readInheritedExtended(mask)).toBe(false);

    const maskDenied = 2; // READ_DENIED
    expect(readInheritedExtended(maskDenied)).toBe(false);
  });

  // Test for readDeniedExtended
  test('should return true when read permission is denied', () => {
    const mask = 2; // READ_DENIED
    expect(readDeniedExtended(mask)).toBe(true);
  });

  test('should return false when read permission is not denied', () => {
    const mask = 1; // READ_ALLOWED
    expect(readDeniedExtended(mask)).toBe(false);
  });

  // Test for writeAllowedExtended
  test('should return true when write permission is allowed', () => {
    const mask = 4; // WRITE_ALLOWED
    expect(writeAllowedExtended(mask)).toBe(true);
  });

  test('should return false when write permission is not allowed', () => {
    const mask = 8; // WRITE_DENIED
    expect(writeAllowedExtended(mask)).toBe(false);
  });

  // Test for writeInheritedExtended
  test('should return true when write permission is inherited (neither allowed nor denied)', () => {
    const mask = 0; // No WRITE permission set
    expect(writeInheritedExtended(mask)).toBe(true);
  });

  test('should return false when write permission is explicitly allowed or denied', () => {
    const mask = 4; // WRITE_ALLOWED
    expect(writeInheritedExtended(mask)).toBe(false);

    const maskDenied = 8; // WRITE_DENIED
    expect(writeInheritedExtended(maskDenied)).toBe(false);
  });

  // Test for writeDeniedExtended
  test('should return true when write permission is denied', () => {
    const mask = 8; // WRITE_DENIED
    expect(writeDeniedExtended(mask)).toBe(true);
  });

  test('should return false when write permission is not denied', () => {
    const mask = 4; // WRITE_ALLOWED
    expect(writeDeniedExtended(mask)).toBe(false);
  });

  // Test for executeAllowedExtended
  test('should return true when execute permission is allowed', () => {
    const mask = 16; // EXECUTE_ALLOWED
    expect(executeAllowedExtended(mask)).toBe(true);
  });

  test('should return false when execute permission is not allowed', () => {
    const mask = 32; // EXECUTE_DENIED
    expect(executeAllowedExtended(mask)).toBe(false);
  });

  // Test for executeInheritedExtended
  test('should return true when execute permission is inherited (neither allowed nor denied)', () => {
    const mask = 0; // No EXECUTE permission set
    expect(executeInheritedExtended(mask)).toBe(true);
  });

  test('should return false when execute permission is explicitly allowed or denied', () => {
    const mask = 16; // EXECUTE_ALLOWED
    expect(executeInheritedExtended(mask)).toBe(false);

    const maskDenied = 32; // EXECUTE_DENIED
    expect(executeInheritedExtended(maskDenied)).toBe(false);
  });

  // Test for executeDeniedExtended
  test('should return true when execute permission is denied', () => {
    const mask = 32; // EXECUTE_DENIED
    expect(executeDeniedExtended(mask)).toBe(true);
  });

  test('should return false when execute permission is not denied', () => {
    const mask = 16; // EXECUTE_ALLOWED
    expect(executeDeniedExtended(mask)).toBe(false);
  });

  // Test for isAllPermissionsInheritedExtended
  test('should return true when all permissions are inherited', () => {
    const mask = 0; // No permissions set, all inherited
    expect(isAllPermissionsInheritedExtended(mask)).toBe(true);
  });

  test('should return false when any permission is explicitly allowed or denied', () => {
    const mask = 1; // READ_ALLOWED
    expect(isAllPermissionsInheritedExtended(mask)).toBe(false);

    const maskDenied = 8; // WRITE_DENIED
    expect(isAllPermissionsInheritedExtended(maskDenied)).toBe(false);
  });
});
