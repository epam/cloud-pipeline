import { ApiError, AuthorizationError, NetworkError } from './errors';
import type { ApiResponse } from './@types';

export function buildFullUrl(
  base: string | undefined,
  uri?: string,
  query = {},
): string {
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
    const { message, status } = JSON.parse(response);
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

export function getByteStreamFromResponse(
  response: Response,
): ReadableStream<Uint8Array> {
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

export async function performFetch(
  url: string,
  options: RequestInit,
): Promise<Response> {
  const { body } = options;
  if (!body || !(body instanceof FormData)) {
    return fetch(url, options);
  }
  const xmlHttpRequest = new XMLHttpRequest();
  xmlHttpRequest.withCredentials = options.credentials
    ? /^(include)$/i.test(options.credentials)
    : false;
  return new Promise((resolve, reject) => {
    let processed = false;
    xmlHttpRequest.upload.onerror = function () {
      if (!processed) {
        processed = true;
        reject(new Error('Fetch error'));
      }
    };
    xmlHttpRequest.onreadystatechange = function () {
      if (xmlHttpRequest.readyState !== 4) return;

      const getHeaders = (): Record<string, string> => {
        const headers = xmlHttpRequest
          .getAllResponseHeaders()
          .split(/\r?\n/)
          .filter((header) => header.length > 0)
          .map((header) => {
            const [key, ...values] = header.split(':');
            return { key, value: values.join(':').trim() };
          });
        return headers.reduce<Record<string, string>>(
          (result, header) => ({
            ...result,
            [header.key]: header.value,
          }),
          {},
        );
      };
      processed = true;
      const responseOptions = {
        status: xmlHttpRequest.status,
        statusText: xmlHttpRequest.statusText,
        headers: getHeaders(),
      };
      const response = xmlHttpRequest.response as unknown;
      if (
        response &&
        (response instanceof Blob ||
          response instanceof ArrayBuffer ||
          typeof response === 'string')
      ) {
        resolve(new Response(response, responseOptions));
        return;
      }
      if (response && typeof response === 'object') {
        resolve(new Response(JSON.stringify(response), responseOptions));
        return;
      }
      resolve(new Response(null, responseOptions));
    };

    xmlHttpRequest.open(options.method ?? 'GET', url);
    xmlHttpRequest.send(body);
  });
}
