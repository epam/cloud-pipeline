export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'OPTIONS' | 'HEAD';

export type AbortRequestOptions = {
  signal?: AbortSignal;
  // 0 - lowest priority
  requestPriority?: number;
};

/** Request body. Plain objects and arrays are JSON-serialized; FormData/Blob/URLSearchParams are sent as-is. */
export type ApiRequestBody = string | FormData | Blob | URLSearchParams | object;

export type ApiBaseRequestOptions = AbortRequestOptions & {
  method?: HttpMethod;
  body?: ApiRequestBody;
  uri?: string;
  url?: string;
  baseUrl?: string;
  query?: Record<string, any>;
  bearer?: string;
  authorization?: string;
  credentials?: RequestCredentials;
  mode?: RequestMode;
  responseTypeRegExp?: RegExp;
  void?: boolean;
  tryAuthenticateOnError?: boolean;
  fetchOnUnauthenticatedService?: boolean;
  contentType?: string;
};

export type ApiJsonRequestOptions<Options extends ApiBaseRequestOptions> = Options & {
  /** When true, use default TTL. When number, TTL in ms. */
  cached?: boolean | number;
};

export type ApiTextRequestOptions<Options extends ApiBaseRequestOptions> = Options & {
  /** When true, use default TTL. When number, TTL in ms. */
  cached?: boolean | number;
};

export type ApiVoidRequestOptions<Options extends ApiBaseRequestOptions> = Options & {
  void: true;
};

export type ApiResponse<Payload> = {
  message?: string;
  payload: Payload;
  status: string;
};

export type ApiServiceState = {
  readonly identifier: string;
  readonly initialized: boolean;
  readonly authenticating: boolean;
  readonly error: string | undefined;
  authenticated: boolean;
  anonymous: boolean;
};

export type ApiServiceOptions = {
  readonly base: string | undefined;
  readonly bearerToken?: string | undefined;
  readonly authorizationBearerToken?: string | undefined;
};

export type ApiServiceAuthentication<
  Service extends ApiService<RequestOptions>,
  RequestOptions extends ApiBaseRequestOptions,
> = <AuthenticationOptions>(service: Service, options?: AuthenticationOptions) => Promise<boolean>;

export type ApiServiceStateChangedCallback = (state: ApiServiceState) => void;

export type ApiService<RequestOptions extends ApiBaseRequestOptions> = ApiServiceState &
  ApiServiceOptions & {
    addApiServiceStateChangedListener(listener: ApiServiceStateChangedCallback): void;
    removeApiServiceStateChangedListener(listener: ApiServiceStateChangedCallback): void;
    getState(): ApiServiceState;

    authenticate<AuthenticateOptions>(options?: AuthenticateOptions): Promise<boolean>;
    setAuthenticationLogic(
      authenticationLogic?: ApiServiceAuthentication<typeof this, RequestOptions>,
    ): void;
    createWaitUntilAuthenticatedLogic(): ApiServiceAuthentication<typeof this, RequestOptions>;

    buildUrl(options: RequestOptions): string;

    apiRequest(options: RequestOptions): Promise<Response>;
    apiStreamRequest(options: RequestOptions): Promise<ReadableStream<Uint8Array>>;
    apiBlobRequest(options: RequestOptions): Promise<Blob>;

    jsonRequest<ResponseType>(
      options: ApiJsonRequestOptions<RequestOptions>,
    ): Promise<ResponseType>;
    jsonGet<ResponseType>(
      options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method' | 'body'>,
    ): Promise<ResponseType>;
    jsonPost<ResponseType>(
      options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
    ): Promise<ResponseType>;
    jsonPut<ResponseType>(
      options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
    ): Promise<ResponseType>;
    jsonDelete<ResponseType>(
      options: Omit<ApiJsonRequestOptions<RequestOptions>, 'method'>,
    ): Promise<ResponseType>;

    textRequest(options: ApiTextRequestOptions<RequestOptions>): Promise<string>;
    textGet(options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>): Promise<string>;
    textPost(options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>): Promise<string>;
    textPut(options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>): Promise<string>;
    textDelete(options: Omit<ApiTextRequestOptions<RequestOptions>, 'method'>): Promise<string>;

    voidRequest(options: Omit<ApiVoidRequestOptions<RequestOptions>, 'void'>): Promise<void>;
    voidGet(options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>): Promise<void>;
    voidPost(
      options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
    ): Promise<void>;
    voidPut(options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>): Promise<void>;
    voidDelete(
      options: Omit<ApiVoidRequestOptions<RequestOptions>, 'method' | 'void'>,
    ): Promise<void>;
  };

export enum ApiServiceLogLevel {
  error = 0,
  warn = 1,
  info = 2,
  debug = 3,
}

export type PagedRequestOptions = {
  page: number;
  pageSize: number;
};
