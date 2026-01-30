/**
 * API client configuration options.
 */
export interface IApiOptions{
    url: string,
    token: string,
}


/**
 * API response wrapper.
 * Corresponds to pipe-cli API response structure.
 */
export interface IApiResponse<T> {
  /** Response status */
  status: string;
  /** Response payload */
  payload?: T;
  /** Error message if status is not OK */
  message?: string;
}
