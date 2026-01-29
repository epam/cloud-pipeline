import { ILogger } from "cp-client-common";
import { APIOptions, APIResponse } from "./types";

/**
 * Base API client class.
 * Corresponds to pipe-cli src/api/base.py:API class.
 */
export class BaseAPI {
  protected apiUrl: string;
  protected apiToken: string;

  constructor(
    options: APIOptions,
    protected readonly logger: ILogger
  ) {
    this.apiUrl = options.apiUrl || process.env.CP_API_URL!;
    this.apiToken = options.apiToken || process.env.CP_API_TOKEN!;

    if (!this.apiToken) {
      throw new Error("API token is required. Set CP_API_TOKEN environment variable or provide apiToken in options.");
    }
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
    const url = `${this.apiUrl}${path}`;
    this.logger.debug(`API call: ${method} ${url}`);

    const headers: Record<string, string> = {
      "Authorization": `Bearer ${this.apiToken}`,
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

    const responseData: APIResponse<T> = await response.json();

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
  protected async callRaw<T>(path: string, method: "GET" | "POST" | "PUT" | "DELETE" = "GET", data?: any): Promise<APIResponse<T>> {
    const url = `${this.apiUrl}/${path}`;
    this.logger?.debug(`API call (raw): ${method} ${url}`);

    const headers: Record<string, string> = {
      "Authorization": `Bearer ${this.apiToken}`,
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
