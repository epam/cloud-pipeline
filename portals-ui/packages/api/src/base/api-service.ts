import type {
  ApiBaseRequestOptions,
  ApiJsonRequestOptions,
  ApiService,
  ApiServiceAuthentication,
  ApiServiceOptions,
  ApiServiceState,
  ApiServiceStateChangedCallback,
  ApiTextRequestOptions,
  ApiVoidRequestOptions,
  HttpMethod,
} from './@types';
import {
  buildFullUrl,
  checkResponseStatus,
  extractApiError,
  getByteStreamFromResponse,
  parseJsonResponse,
  performFetch,
} from './utilities';
import RequestsCache from './cache';
import {
  ApiError,
  ApiInitializationError,
  AuthorizationError,
  NetworkError,
} from './errors';
import RequestsQueue from './requests-queue';
import { ApiServiceLogLevel } from './log-level.ts';

abstract class BaseApiService<
    Options extends ApiServiceOptions,
    RequestOptions extends ApiBaseRequestOptions,
  >
  extends RequestsQueue
  implements ApiService<RequestOptions>
{
  private readonly _identifier: string;
  private readonly _logLevel: ApiServiceLogLevel;
  private _base: string | undefined;
  private _options: Options | undefined;
  private _bearerToken: string | undefined;
  private _authorizationBearerToken: string | undefined;
  private _authenticated: boolean;
  private _authenticating: boolean;
  private _error: string | undefined;
  private _authenticationLogic:
    | ApiServiceAuthentication<this, RequestOptions>
    | undefined;
  private _authenticationLogicPromise: Promise<boolean> | undefined;
  private readonly _requestsCache: RequestsCache;
  private _stateChangedListeners: ApiServiceStateChangedCallback[];
  private _waitUntilAuthenticatedPromise: Promise<void> | undefined;
  private _waitUntilAuthenticatedCallback: (() => void) | undefined;

  protected constructor(
    identifier: string,
    logLevel = ApiServiceLogLevel.info,
  ) {
    super();
    this._logLevel = logLevel;
    this._identifier = identifier;
    this._options = undefined;
    this._authenticated = false;
    this._authenticating = false;
    this._error = undefined;
    this._requestsCache = new RequestsCache();
    this._stateChangedListeners = [];
    this._waitUntilAuthenticatedPromise = undefined;
    this._waitUntilAuthenticatedCallback = undefined;
  }

  get identifier(): string {
    return this._identifier;
  }

  get base(): string | undefined {
    return this._base;
  }

  private set base(base: string | undefined) {
    this._base = base;
  }

  get initialized(): boolean {
    return Boolean(this._options);
  }

  get authenticated(): boolean {
    return this._authenticated;
  }

  set authenticated(authenticated: boolean) {
    if (this._authenticated !== authenticated) {
      this._authenticated = authenticated;
      if (authenticated) {
        if (this._waitUntilAuthenticatedCallback) {
          this._waitUntilAuthenticatedCallback();
        }
        this._waitUntilAuthenticatedCallback = undefined;
        this._waitUntilAuthenticatedPromise = undefined;
      } else if (!authenticated) {
        if (!this._waitUntilAuthenticatedPromise) {
          this._waitUntilAuthenticatedPromise = new Promise((resolve) => {
            this._waitUntilAuthenticatedCallback = resolve;
          });
        }
      }
      this.reportState();
    }
  }

  get authenticating(): boolean {
    return this._authenticating;
  }

  private set authenticating(authenticating: boolean) {
    if (this._authenticating !== authenticating) {
      this._authenticating = authenticating;
      this.reportState();
    }
  }

  get error(): string | undefined {
    return this._error;
  }

  private set error(error: string | undefined) {
    if (this._error !== error) {
      this._error = error;
      if (error) {
        this.logError('error:', error);
      }
      this.reportState();
    }
  }

  get bearerToken(): string | undefined {
    return this._bearerToken;
  }

  protected set bearerToken(bearerToken: string | undefined) {
    this._bearerToken = bearerToken;
  }

  get authorizationBearerToken(): string | undefined {
    return this._authorizationBearerToken;
  }

  protected set authorizationBearerToken(
    authorizationBearerToken: string | undefined,
  ) {
    this._authorizationBearerToken = authorizationBearerToken;
  }

  addApiServiceStateChangedListener(
    listener: ApiServiceStateChangedCallback,
  ): void {
    this.removeApiServiceStateChangedListener(listener);
    this._stateChangedListeners.push(listener);
    listener(this.getState());
  }

  removeApiServiceStateChangedListener(
    listener: ApiServiceStateChangedCallback,
  ): void {
    this._stateChangedListeners = this._stateChangedListeners.filter(
      (l) => l !== listener,
    );
  }

  getState(): ApiServiceState {
    return {
      identifier: this.identifier,
      authenticating: this.authenticating,
      authenticated: this.authenticated,
      error: this.error,
      initialized: this.initialized,
    };
  }

  initialize(
    options: Options,
    authenticationLogic?: ApiServiceAuthentication<
      ApiService<RequestOptions>,
      RequestOptions
    >,
  ): void {
    this.log('initializing with options', { ...options });
    this.base = options.base;
    this.bearerToken = options.bearerToken;
    this.authorizationBearerToken = options.authorizationBearerToken;
    this._options = { ...options };
    this.setAuthenticationLogic(authenticationLogic);
  }

  setAuthenticationLogic(
    authenticationLogic?: ApiServiceAuthentication<
      ApiService<RequestOptions>,
      RequestOptions
    >,
  ): void {
    this._authenticationLogic = authenticationLogic
      ? authenticationLogic.bind(this)
      : undefined;
  }

  createWaitUntilAuthenticatedLogic(): ApiServiceAuthentication<
    ApiService<RequestOptions>,
    RequestOptions
  > {
    return async (): Promise<boolean> => {
      if (this._waitUntilAuthenticatedPromise) {
        await this._waitUntilAuthenticatedPromise;
      }
      return this.authenticated;
    };
  }

  async authenticate<AuthenticateOptions>(
    options?: AuthenticateOptions,
  ): Promise<boolean> {
    if (!this._authenticationLogicPromise) {
      this._authenticationLogicPromise = this.authenticationLogic(options);
      this.authenticating = true;
      this._authenticationLogicPromise
        .then((authenticated) => {
          this.authenticated = authenticated;
          this.authenticating = false;
          this.error = undefined;
          this._authenticationLogicPromise = undefined;
          this.reportState();
        })
        .catch((error) => {
          this.authenticated = false;
          this.authenticating = false;
          this.error = error instanceof Error ? error.message : undefined;
          this._authenticationLogicPromise = undefined;
          this.reportState();
        });
    }
    return this._authenticationLogicPromise;
  }

  buildUrl(options: RequestOptions): string {
    return this.getBasicFetchOptions(options).url;
  }

  async getFetchOptions(options: RequestOptions): Promise<{
    url: string;
    method: HttpMethod;
    options: RequestInit;
  }> {
    const {
      body,
      bearer = this.bearerToken,
      authorization = this.authorizationBearerToken,
      credentials = 'include',
      mode = 'cors',
      signal,
      contentType,
    } = options;
    let fetchBody: FormData | ArrayBuffer | string | undefined;
    const headers: Record<string, string> = {};
    if (contentType) {
      headers['Content-Type'] = contentType;
    }
    if (body && body instanceof FormData) {
      fetchBody = body;
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
    } else if (body && body instanceof ArrayBuffer) {
      fetchBody = body;
    } else if (typeof body === 'object') {
      fetchBody = JSON.stringify(body);
      headers['Content-Type'] = 'application/json;charset=UTF-8';
    } else if (typeof body === 'string') {
      fetchBody = body;
    }
    const { url, method } = this.getBasicFetchOptions(options);
    if (bearer) {
      headers.bearer = bearer;
    }
    if (authorization) {
      headers.Authorization = authorization;
    }
    const fetchOptions: RequestInit = {
      method,
      headers,
      body: fetchBody,
      credentials,
      mode,
      signal,
      redirect: 'error',
    };
    return {
      url,
      method,
      options: fetchOptions,
    };
  }

  async apiRequest(options: RequestOptions): Promise<Response> {
    if (!this.initialized) {
      this.logError('trying to perform request on not-initialized service');
      throw new ApiInitializationError(
        `${this.identifier} service not initialized`,
      );
    }
    let { fetchOnUnauthenticatedService = false } = options;
    const { tryAuthenticateOnError = true } = options;
    let authenticationPerformed = !tryAuthenticateOnError;
    const apiRequestTry = async (): Promise<Response> => {
      if (!this.authenticated && !fetchOnUnauthenticatedService) {
        this.log(
          'api request',
          this.getApiRequestDescription(options),
          'suspended: service not authenticated',
        );
        authenticationPerformed = true;
        const authResult = await this.authenticate();
        if (!authResult) {
          throw new AuthorizationError();
        }
      }
      try {
        return await this.performApiRequest(options);
      } catch (error) {
        if (error instanceof AuthorizationError) {
          this.authenticated = false;
          if (!authenticationPerformed) {
            // try again
            fetchOnUnauthenticatedService = false;
            return apiRequestTry();
          }
        }
        if (error instanceof Error) {
          throw error;
        }
        throw new Error('Error performing request');
      }
    };
    return apiRequestTry();
  }

  async apiStreamRequest(
    options: RequestOptions,
  ): Promise<ReadableStream<Uint8Array>> {
    const response = await this.apiRequest({
      responseTypeRegExp: /^application\/octet-stream$/i,
      ...options,
    });
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

  async apiStreamRequestQueued<T>(
    options: RequestOptions,
    process: (stream: ReadableStream<Uint8Array>) => Promise<T>,
  ): Promise<T> {
    return this.queue(async () => {
      const response = await this.apiRequest({
        responseTypeRegExp: /^application\/octet-stream$/i,
        ...options,
      });
      const stream = getByteStreamFromResponse(response);
      return process(stream);
    }, options);
  }

  async jsonRequest<ResponseType>(
    options: ApiJsonRequestOptions<RequestOptions>,
  ): Promise<ResponseType> {
    const { analyseResponse = true } = options;
    const text = await this.textRequest(options);
    if (!analyseResponse) {
      return JSON.parse(text) as ResponseType;
    }
    const { payload, message, status } = parseJsonResponse<ResponseType>(text);
    if (/^ok$/i.test(status)) {
      return payload;
    }
    throw new ApiError(message ?? 'Error performing request');
  }

  async jsonGet<ResponseType>(
    options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method' | 'body'>,
  ): Promise<ResponseType> {
    return this.jsonRequest<ResponseType>({
      ...options,
      method: 'GET',
    } as ApiJsonRequestOptions<RequestOptions>);
  }

  async jsonPost<ResponseType>(
    options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
  ): Promise<ResponseType> {
    return this.jsonRequest<ResponseType>({
      ...options,
      method: 'POST',
    } as ApiJsonRequestOptions<RequestOptions>);
  }

  async jsonPut<ResponseType>(
    options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
  ): Promise<ResponseType> {
    return this.jsonRequest<ResponseType>({
      ...options,
      method: 'PUT',
    } as ApiJsonRequestOptions<RequestOptions>);
  }

  async jsonDelete<ResponseType>(
    options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
  ): Promise<ResponseType> {
    return this.jsonRequest<ResponseType>({
      ...options,
      method: 'DELETE',
    } as ApiJsonRequestOptions<RequestOptions>);
  }

  async textRequest(
    options: ApiTextRequestOptions<RequestOptions>,
  ): Promise<string> {
    const cached = options.cached ?? false;
    const { url, method = 'GET' } = await this.getFetchOptions(
      options as RequestOptions,
    );
    const doRequest = async (): Promise<string> => {
      if (
        method === 'GET' &&
        cached &&
        this._requestsCache.hasCachedRequest(url)
      ) {
        return this._requestsCache.getCachedRequest(url)!;
      }
      return this.queue(async () => {
        const response = await this.apiRequest({
          ...options,
        } as RequestOptions);
        const text = await response.text();
        if (method === 'GET' && cached) {
          this._requestsCache.addCachedRequest(url, text);
        }
        return text;
      }, options);
    };
    try {
      return await doRequest();
    } catch (error) {
      if (
        error instanceof ApiError ||
        error instanceof AuthorizationError ||
        error instanceof NetworkError
      ) {
        throw error;
      } else if (error instanceof Error) {
        throw new NetworkError(error.message);
      } else {
        throw new Error(`Error fetching ${url}`);
      }
    }
  }

  async textGet(
    options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>,
  ): Promise<string> {
    return this.textRequest({
      ...options,
      method: 'GET',
    } as ApiTextRequestOptions<RequestOptions>);
  }

  async textPost(
    options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>,
  ): Promise<string> {
    return this.textRequest({
      ...options,
      method: 'POST',
    } as ApiTextRequestOptions<RequestOptions>);
  }

  async textPut(
    options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>,
  ): Promise<string> {
    return this.textRequest({
      ...options,
      method: 'PUT',
    } as ApiTextRequestOptions<RequestOptions>);
  }

  async textDelete(
    options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>,
  ): Promise<string> {
    return this.textRequest({
      ...options,
      method: 'DELETE',
    } as ApiTextRequestOptions<RequestOptions>);
  }

  async voidRequest(
    options: Omit<ApiVoidRequestOptions<RequestOptions>, 'void'>,
  ): Promise<void> {
    await this.queue(
      async () => this.apiRequest({ ...options, void: true } as RequestOptions),
      options,
    );
  }

  async voidGet(
    options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
  ): Promise<void> {
    await this.voidRequest({
      ...options,
      method: 'GET',
    } as ApiVoidRequestOptions<RequestOptions>);
  }

  async voidPost(
    options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
  ): Promise<void> {
    await this.voidRequest({
      ...options,
      method: 'POST',
    } as ApiVoidRequestOptions<RequestOptions>);
  }

  async voidPut(
    options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
  ): Promise<void> {
    await this.voidRequest({
      ...options,
      method: 'PUT',
    } as ApiVoidRequestOptions<RequestOptions>);
  }

  async voidDelete(
    options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
  ): Promise<void> {
    await this.voidRequest({
      ...options,
      method: 'DELETE',
    } as ApiVoidRequestOptions<RequestOptions>);
  }

  async apiBlobRequest(options: RequestOptions): Promise<Blob> {
    return this.queue(async () => {
      const response = await this.apiRequest(options);
      return response.blob();
    }, options);
  }

  protected reportState(): void {
    const state = this.getState();
    for (const listener of this._stateChangedListeners) {
      listener(state);
    }
  }

  protected log(...message: Parameters<typeof console.log>): void {
    if (this.shouldLogMessageOfLevel(ApiServiceLogLevel.debug)) {
      console.log(`[${this.identifier}]`, ...message);
    }
  }

  protected info(...message: Parameters<typeof console.log>): void {
    if (this.shouldLogMessageOfLevel(ApiServiceLogLevel.info)) {
      console.info(`[${this.identifier}]`, ...message);
    }
  }

  protected warn(...message: Parameters<typeof console.log>): void {
    if (this.shouldLogMessageOfLevel(ApiServiceLogLevel.warn)) {
      console.warn(`[${this.identifier}]`, ...message);
    }
  }

  protected logError(...message: Parameters<typeof console.log>): void {
    if (this.shouldLogMessageOfLevel(ApiServiceLogLevel.error)) {
      console.error(`[${this.identifier}]`, ...message);
    }
  }

  protected getBasicFetchOptions(options: RequestOptions): {
    url: string;
    method: HttpMethod;
  } {
    const {
      query = {},
      uri,
      baseUrl = this.base,
      url = uri
        ? buildFullUrl(baseUrl, uri, query)
        : buildFullUrl(baseUrl, undefined, query),
      body,
      method = body ? 'POST' : 'GET',
    } = options;
    return {
      url,
      method,
    };
  }

  private getApiRequestDescription(options: RequestOptions): string {
    const { url, method } = this.getBasicFetchOptions(options);
    return `${method} ${url}`;
  }

  private shouldLogMessageOfLevel(level: ApiServiceLogLevel): boolean {
    return this._logLevel >= level;
  }

  private async performApiRequest(options: RequestOptions): Promise<Response> {
    try {
      const { url, options: fetchOptions } =
        await this.getFetchOptions(options);
      const { responseTypeRegExp } = options;
      this.log(
        'performing api request',
        this.getApiRequestDescription(options),
        'with options',
        options,
      );
      const response = await performFetch(url, fetchOptions);
      checkResponseStatus(response);
      const responseType = response.headers.get('Content-Type');
      this.log(
        'performing api request',
        this.getApiRequestDescription(options),
        'response with type',
        responseType ?? '<EMPTY>',
        'received',
      );
      if (
        responseTypeRegExp &&
        (!responseType || !responseTypeRegExp.test(responseType)) &&
        !options.void
      ) {
        const text = await response.text();
        const error = extractApiError(text);
        if (error) {
          throw new ApiError(error);
        }
        throw new ApiError('Error performing request');
      }
      return response;
    } catch (error) {
      if (error instanceof Error) {
        this.warn(
          'error performing api request',
          this.getApiRequestDescription(options),
          ':',
          error.message,
        );
        throw error;
      }
      throw new Error('Error performing request');
    }
  }

  private async authenticationLogic<AuthenticationOptions>(
    options?: AuthenticationOptions,
  ): Promise<boolean> {
    if (!this.initialized) {
      throw new Error(`${this.identifier} not initialized`);
    }
    if (!this._authenticationLogic) {
      return true;
    }
    this.info('authenticating...');
    const authenticated = await this._authenticationLogic(this, options);
    if (authenticated) {
      this.info('authenticated');
    } else {
      this.logError('not authenticated');
    }
    return authenticated;
  }
}

export { BaseApiService };
