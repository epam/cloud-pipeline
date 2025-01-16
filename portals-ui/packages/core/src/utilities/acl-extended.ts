function checkPermission(mask: number, permission: number): boolean {
  return (mask & permission) == permission;
}
const READ_ALLOWED = 0b000001;
const READ_DENIED = 0b000010;
const WRITE_ALLOWED = 0b000100;
const WRITE_DENIED = 0b001000;
const EXECUTE_ALLOWED = 0b010000;
const EXECUTE_DENIED = 0b100000;

export function readAllowedExtended(mask: number): boolean {
  return checkPermission(mask, READ_ALLOWED);
}

export function readInheritedExtended(mask: number): boolean {
  return (
    !checkPermission(mask, READ_ALLOWED) && !checkPermission(mask, READ_DENIED)
  );
}

export function readDeniedExtended(mask: number): boolean {
  return checkPermission(mask, READ_DENIED);
}

export function writeAllowedExtended(mask: number): boolean {
  return checkPermission(mask, WRITE_ALLOWED);
}

export function writeInheritedExtended(mask: number): boolean {
  return (
    !checkPermission(mask, WRITE_ALLOWED) &&
    !checkPermission(mask, WRITE_DENIED)
  );
}

export function writeDeniedExtended(mask: number): boolean {
  return checkPermission(mask, WRITE_DENIED);
}

export function executeAllowedExtended(mask: number): boolean {
  return checkPermission(mask, EXECUTE_ALLOWED);
}

export function executeInheritedExtended(mask: number): boolean {
  return (
    !checkPermission(mask, EXECUTE_ALLOWED) &&
    !checkPermission(mask, EXECUTE_DENIED)
  );
}

export function executeDeniedExtended(mask: number): boolean {
  return checkPermission(mask, EXECUTE_DENIED);
}
