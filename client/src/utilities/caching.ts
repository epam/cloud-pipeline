export type TimestampedObject = {
  timestamp: number;
};

export type CachedObject<T> = TimestampedObject & {
  object: T;
};

export function createCachedObject<T>(object: T): CachedObject<T> {
  return {
    object,
    timestamp: new Date().getTime(),
  };
}

export function isCachedObjectInvalid<T>(
  object: CachedObject<T>,
  validIntervalMilliseconds: number,
): boolean;
export function isCachedObjectInvalid<T>(
  objectTimestamp: number,
  validIntervalMilliseconds: number,
): boolean;
export function isCachedObjectInvalid<T>(
  object: CachedObject<T> | number,
  validIntervalMilliseconds: number,
): boolean {
  const timestamp = typeof object === 'number' ? object : object.timestamp;
  return new Date().getTime() - timestamp > validIntervalMilliseconds;
}

export function updateCachedObject<T>(object: CachedObject<T>): CachedObject<T> {
  return createCachedObject<T>(object.object);
}
