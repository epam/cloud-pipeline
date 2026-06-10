import {ApiError, AuthorizationError, NetworkError} from './errors';
import type {ApiResponse} from '../types';

export function isNativeRequestBody(body: unknown): body is FormData | Blob | URLSearchParams {
  return body instanceof FormData || body instanceof Blob || body instanceof URLSearchParams;
}

export function serializeRequestBody(
  body: unknown,
  headers: Record<string, string>,
  contentType?: string,
): BodyInit | undefined {
  if (body === undefined || body === null) {
    return undefined;
  }
  if (isNativeRequestBody(body)) {
    return body;
  }
  if (typeof body === 'string') {
    if (contentType) {
      headers['Content-Type'] = contentType;
    }
    return body;
  }
  if (typeof body === 'object') {
    headers['Content-Type'] = contentType ?? 'application/json;charset=UTF-8';
    return JSON.stringify(body);
  }
  return undefined;
}

export function normalizeBaseUrl(base: string | undefined): string | undefined {
  if (base === undefined) {
    return undefined;
  }
  try {
    const {href, search = ''} = new URL(base, window.location.href);
    let url = href;
    if (search.length > 0 && url.endsWith(search)) {
      url = url.substring(0, url.length - search.length);
    }
    return url;
  } catch {
    return base;
  }
}

export function buildFullUrl(base: string | undefined, uri?: string, query = {}): string {
  let urlBase = base ?? '';
  if (urlBase.endsWith('/')) {
    urlBase = urlBase.slice(0, -1);
  }
  let uriCorrected = uri ?? '';
  if (uriCorrected.startsWith('/')) {
    uriCorrected = uriCorrected.slice(1);
  }
  urlBase = `${urlBase}/${uriCorrected}`;
  const queryString = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(
      ([parameter, value]) =>
        `${encodeURIComponent(parameter)}=${encodeURIComponent(`${value as string}`)}`,
    )
    .join('&');
  if (queryString.length > 0) {
    urlBase = urlBase.concat(`?${queryString}`);
  }
  return urlBase;
}

export function checkResponseStatus(response: Response): never | void {
  if (response.status === 401) {
    throw new AuthorizationError();
  }
  if (response.status >= 300 && response.status < 500) {
    throw new NetworkError(`Network error (code ${response.status})`);
  }
  if (response.status >= 500 && response.status < 600) {
    throw new ApiError(`Error performing request (code ${response.status})`);
  }
}

export function extractApiError(response: string): string | undefined {
  try {
    const {message, status} = JSON.parse(response);
    if (typeof status === 'string' && /^error$/i.test(status)) {
      return message as string;
    }
  } catch {
    // noop
  }
  return undefined;
}

export function parseJsonResponse<Payload>(text: string): ApiResponse<Payload> {
  try {
    return JSON.parse(text) as ApiResponse<Payload>;
  } catch (parseError) {
    throw new ApiError(
      parseError instanceof Error
        ? `Unsupported response received (JSON format expected); error: ${parseError.message}`
        : 'Unsupported response received (JSON format expected)',
    );
  }
}

export function getByteStreamFromResponse(response: Response): ReadableStream<Uint8Array> {
  return (
    response.body ??
    new ReadableStream({
      type: 'bytes',
      start(controller) {
        controller.close();
      },
    })
  );
}
