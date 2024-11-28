function checkPermission(mask: number, permission: number): boolean {
  return (mask & permission) == permission;
}
const READ_PERMISSION = 0b0001;
const WRITE_PERMISSION = 0b0010;
const EXECUTE_PERMISSION = 0b0100;
const OWNER_PERMISSION = 0b1000;

export function readAllowed(mask: number): boolean {
  return checkPermission(mask, READ_PERMISSION);
}

export function writeAllowed(mask: number): boolean {
  return checkPermission(mask, WRITE_PERMISSION);
}

export function executeAllowed(mask: number): boolean {
  return checkPermission(mask, EXECUTE_PERMISSION);
}

export function ownerAllowed(mask: number): boolean {
  return checkPermission(mask, OWNER_PERMISSION);
}
