import { ILogger } from "cp-client-common";
import { IApiOptions, IApiResponse } from "./types/api";
import { fetchWithHooks as fetchWithHook } from "./fetch-with-hook";

/**
 * Base API client class.
 * Corresponds to pipe-cli src/api/base.py:API class.
 */
export class BaseAPI {

  constructor(
    protected apiOpts: IApiOptions,
    protected readonly logger: ILogger
  ) {
  }

  /**
   * Make API call to Cloud Pipeline REST API.
   * Corresponds to pipe-cli API.call() method.
   * 
   * @param path - API endpoint path (relative to /restapi/)
   * @param method - HTTP method (default: GET)
   * @param data - Request body for POST/PUT requests
   * @returns API response payload
   */
  protected async call<T>(path: string, method: "GET" | "POST" | "PUT" | "DELETE" = "GET", data?: any): Promise<T> {
    const url = `${this.apiOpts.url}${path}`;
    this.logger.debug(`API call: ${method} ${url}`);

    const headers: Record<string, string> = {
      "Authorization": `Bearer ${this.apiOpts.token}`,
      "Content-Type": "application/json",
    };

    const options: RequestInit = {
      method,
      headers,
    };

    if (data && (method === "POST" || method === "PUT")) {
      options.body = JSON.stringify(data);
    }

    const response = await fetchWithHook(url, options,
      (_response: Response, _from: string | URL, to: URL, _step: number) => {
        // const responseTxt = JSON.stringify({
        //   status: response.status,
        //   type: response.type,
        //   headers: Object.fromEntries(response.headers.entries()),
        // }, null, 2);
        // this.logger.debug(`Redirect:\n${indent(2, responseTxt)}`);
        // return true;
        if (to.pathname.includes('saml/discovery'))
          throw new Error('SAML discovery redirect detected. API token failed to authenticate.');
        return true;
      });

    if (!response.ok) {
      const errorText = await response.text().catch(() => response.statusText);
      throw new Error(`API request failed: ${response.status} ${errorText}`);
    }

    const responseData: IApiResponse<T> = await response.json();

    if (responseData.message) {
      throw new Error(responseData.message);
    }

    if (responseData.payload === undefined) {
      throw new Error("API response missing payload");
    }

    return responseData.payload;
  }

  /**
   * Get API response without extracting payload.
   * Used when full response structure is needed.
   */
  protected async callRaw<T>(path: string, method: "GET" | "POST" | "PUT" | "DELETE" = "GET", data?: any): Promise<IApiResponse<T>> {
    const url = `${this.apiOpts.url}${path}`;
    this.logger?.debug(`API call (raw): ${method} ${url}`);

    const headers: Record<string, string> = {
      "Authorization": `Bearer ${this.apiOpts.token}`,
      "Content-Type": "application/json",
    };

    const options: RequestInit = {
      method,
      headers,
    };

    if (data && (method === "POST" || method === "PUT")) {
      options.body = JSON.stringify(data);
    }

    const response = await fetch(url, options);

    if (!response.ok) {
      const errorText = await response.text().catch(() => response.statusText);
      throw new Error(`API request failed: ${response.status} ${errorText}`);
    }

    return await response.json();
  }
}
