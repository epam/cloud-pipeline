import {
  AbortError,
  logError,
  createNumberIdentifierGenerator,
  createIdentifierGenerator,
  noop,
  escapeRegExp,
  correctPath,
  joinPath,
  parentPath,
  capitalizedString,
  unCapitalizedString,
  createSingleCallPromise,
  asyncFilter,
} from '../../src';

describe('Utility Functions', () => {
  // Test for AbortError
  test('AbortError should have a default message "Aborted"', () => {
    const error = new AbortError();
    expect(error.message).toBe('Aborted');
  });

  test('AbortError should accept a custom message', () => {
    const error = new AbortError('Custom Abort');
    expect(error.message).toBe('Custom Abort');
  });

  // Test for logError
  test('logError should log a message when error is not AbortError', () => {
    console.warn = jest.fn();
    const error = new Error('Test error');
    logError(error, 'Custom message');
    expect(console.warn).toHaveBeenCalledWith('Custom message: Test error');

    logError('Test error', 'Custom message');
    expect(console.warn).toHaveBeenCalledWith('Custom message: Test error');

    logError('Test error');
    expect(console.warn).toHaveBeenCalledWith('Test error');
  });

  test('logError should not log anything when error is AbortError', () => {
    console.warn = jest.fn();
    const error = new AbortError();
    logError(error);
    expect(console.warn).not.toHaveBeenCalled();
  });

  // Test for createNumberIdentifierGenerator
  test('createNumberIdentifierGenerator should generate sequential numbers', () => {
    const generator = createNumberIdentifierGenerator();
    expect(generator()).toBe(1);
    expect(generator()).toBe(2);
    expect(generator()).toBe(3);
  });

  // Test for createIdentifierGenerator
  test('createIdentifierGenerator should generate identifiers with numbers', () => {
    const generator = createIdentifierGenerator('user');
    expect(generator()).toBe('user-1');
    expect(generator()).toBe('user-2');
  });

  test('createIdentifierGenerator should generate identifiers without prefix if no name is provided', () => {
    const generator = createIdentifierGenerator();
    expect(generator()).toBe('1');
    expect(generator()).toBe('2');
  });

  // Test for noop
  test('noop should not throw any errors or do anything', () => {
    expect(() => noop()).not.toThrow();
  });

  // Test for escapeRegExp
  test('escapeRegExp should escape special characters in string', () => {
    const input = 'a+b.c?d(e)f[g]h{i}j^k$l*m';
    const result = escapeRegExp(input);
    const expected = 'a\\+b\\.c\\?d\\(e\\)f\\[g\\]h\\{i\\}j\\^k\\$l\\*m';
    expect(result).toBe(expected);
  });

  // Test for correctPath
  test('correctPath should ensure a leading slash when ensureLeadingSlash is true', () => {
    expect(correctPath('test', { ensureLeadingSlash: true })).toBe('/test');
  });

  test('correctPath should ensure a trailing slash when ensureTrailingSlash is true', () => {
    expect(correctPath('test', { ensureTrailingSlash: true })).toBe('test/');
  });

  test('correctPath should remove leading slash when removeLeadingSlash is true', () => {
    expect(correctPath('/test', { removeLeadingSlash: true })).toBe('test');
  });

  test('correctPath should remove trailing slash when removeTrailingSlash is true', () => {
    expect(correctPath('test/', { removeTrailingSlash: true })).toBe('test');
  });

  test('correctPath should handle undefined path', () => {
    expect(correctPath(undefined)).toBe('');
  });

  // Test for joinPath
  test('joinPath should join multiple path segments correctly', () => {
    const result = joinPath('/folder', '/file', '/test');
    expect(result).toBe('/folder/file/test');
  });

  test('joinPath should return an empty string when no paths are provided', () => {
    expect(joinPath()).toBe('');
  });

  // Test for relative and absolute Linux paths
  test('parentPath should return the parent directory of a relative Linux path', () => {
    expect(parentPath('some-path/file')).toBe('some-path');
  });

  test('parentPath should return the parent directory of an absolute Linux path', () => {
    expect(parentPath('/some-path/file')).toBe('/some-path');
  });

  // Test for file:// paths
  test('parentPath should convert file:// paths and return the parent directory', () => {
    expect(parentPath('file://some-path/file')).toBe('/some-path');
  });

  // Test for HTTP/HTTPS/FTP paths
  test('parentPath should return the correct parent path for URLs (http)', () => {
    expect(parentPath('http://example.com/some/path')).toBe('http://example.com/some');
  });

  test('parentPath should return only the protocol and hostname for URLs without path (http)', () => {
    expect(parentPath('http://example.com')).toBe('http://example.com');
  });

  test('parentPath should return the correct parent path for URLs (https)', () => {
    expect(parentPath('https://example.com/some/path')).toBe('https://example.com/some');
  });

  test('parentPath should return only the protocol and hostname for URLs without path (https)', () => {
    expect(parentPath('https://example.com')).toBe('https://example.com');
  });

  test('parentPath should return the correct parent path for URLs (ftp)', () => {
    expect(parentPath('ftp://example.com/some/path')).toBe('ftp://example.com/some');
  });

  test('parentPath should return only the protocol and hostname for URLs without path (ftp)', () => {
    expect(parentPath('ftp://example.com')).toBe('ftp://example.com');
  });

  test('parentPath should handle file:// URLs with trailing slash correctly', () => {
    expect(parentPath('file://some-path/file/')).toBe('/some-path');
  });

  test('parentPath should handle URLs with trailing slash correctly (http)', () => {
    expect(parentPath('http://example.com/some/path/')).toBe('http://example.com/some');
  });

  test('parentPath should handle URLs with trailing slash correctly (https)', () => {
    expect(parentPath('https://example.com/some/path/')).toBe('https://example.com/some');
  });

  test('parentPath should handle URLs with trailing slash correctly (ftp)', () => {
    expect(parentPath('ftp://example.com/some/path/')).toBe('ftp://example.com/some');
  });

  // Test for capitalizedString
  test('capitalizedString should capitalize the first letter of a string', () => {
    expect(capitalizedString('hello')).toBe('Hello');
  });

  test('capitalizedString should return empty string if input is empty', () => {
    expect(capitalizedString('')).toBe('');
    expect(capitalizedString(undefined)).toBe('');
  });

  // Test for unCapitalizedString
  test('unCapitalizedString should uncapitalize the first letter of a string', () => {
    expect(unCapitalizedString('Hello')).toBe('hello');
  });

  test('unCapitalizedString should return empty string if input is empty', () => {
    expect(unCapitalizedString('')).toBe('');
    expect(unCapitalizedString(undefined)).toBe('');
  });

  // Test for createSingleCallPromise
  test('createSingleCallPromise should ensure the function is called only once', async () => {
    const fn = jest.fn().mockResolvedValue(42);
    const singleCallFn = createSingleCallPromise(fn);

    await singleCallFn();
    await singleCallFn();

    expect(fn).toHaveBeenCalledTimes(1);
  });

  test('createSingleCallPromise should ensure re-call function if it was failed', async () => {
    const mockFunction = jest.fn(() => Promise.reject(new Error('Failure')));
    const singleCallPromise = createSingleCallPromise(mockFunction, true);

    // First call should fail and reset the promise
    await expect(singleCallPromise()).rejects.toThrow('Failure');

    // Second call should be allowed after failure
    mockFunction.mockClear(); // Clear previous calls
    await expect(singleCallPromise()).rejects.toThrow('Failure'); // Should reject again due to reset

    // Call the promise after reset
    const successMockFunction = jest.fn(() => Promise.resolve('Success'));
    const successSingleCallPromise = createSingleCallPromise(successMockFunction, true);

    // First call should succeed
    await expect(successSingleCallPromise()).resolves.toBe('Success');
    expect(successMockFunction).toHaveBeenCalledTimes(1);

    // Second call should succeed as well after it was successful
    await expect(successSingleCallPromise()).resolves.toBe('Success');
    expect(successMockFunction).toHaveBeenCalledTimes(1); // Should not call again after successful execution
  });

  // Test for asyncFilter
  test('asyncFilter should filter elements asynchronously', async () => {
    const elements = [1, 2, 3, 4, 5];
    const result = await asyncFilter(elements, (element) => element > 2);
    expect(result).toEqual([3, 4, 5]);
  });

  test('asyncFilter should throw an error if the callback throws an error', async () => {
    const elements = [1, 2, 3];

    // Create a callback that throws an error
    const callback = (element: number) => {
      if (element === 2) {
        throw new Error('Callback error');
      }
      return element % 2 === 0;
    };

    // Test that asyncFilter throws the expected error when callback throws an error
    await expect(asyncFilter(elements, callback)).rejects.toThrow('Callback error');
  });

  test('asyncFilter should respect the abortSignal and reject with AbortError', async () => {
    const abortController = new AbortController();
    abortController.abort();
    const elements = [1, 2, 3, 4, 5];
    const promise = asyncFilter(elements, (element) => element > 2, { abortSignal: abortController.signal });
    await expect(promise).rejects.toThrow(AbortError);
  });

  test('asyncFilter should perform multiple batches as separate macrotask', async () => {
    // Mock timer functions to control async behavior
    jest.useFakeTimers();

    const batchSize = 100;
    const batches = 10;

    const elements = Array.from({ length: batches * batchSize }, (_, index) => index + 1); // Create a dataset larger than the batch size (10000)

    // A simple callback that just returns true for even numbers
    const callback = (element: number) => element % 2 === 0;

    // We need to capture how many times setTimeout is called
    const setTimeoutSpy = jest.spyOn(global, 'setTimeout');
    // Run asyncFilter
    const result = asyncFilter(elements, callback, { batch: batchSize });
    jest.runAllTimers();
    const filteredResult = await result;

    // Verify that the setTimeout was called multiple times (one for each batch)
    // The total number of setTimeout calls should be at least 2 (since we're working with a dataset of 20,000 and a batch size of 10,000)
    expect(setTimeoutSpy).toHaveBeenCalledTimes(Math.max(0, batches - 1));
    // Verify that the result is correct (only even numbers)
    expect(filteredResult).toEqual(elements.filter(callback));
    // Clean up timer mocks
    jest.useRealTimers();
  });
});
