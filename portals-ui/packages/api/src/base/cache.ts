const defaultCacheTimeoutSeconds = 60 * 5; // 5 minutes

export default class RequestsCache {
  private readonly _cache: Map<string, string>;
  private readonly _invalidations: Map<string, ReturnType<typeof setTimeout>>;
  constructor() {
    this._cache = new Map<string, string>();
    this._invalidations = new Map<string, ReturnType<typeof setTimeout>>();
  }

  addCachedRequest(
    url: string,
    data: string,
    timeoutSeconds = defaultCacheTimeoutSeconds,
  ): void {
    this.addCachedValue(url, data, timeoutSeconds * 1000);
  }

  getCachedRequest(url: string): string | undefined {
    if (this._cache.has(url)) {
      return this._cache.get(url)!;
    }
    return undefined;
  }

  hasCachedRequest(url: string): boolean {
    return this._cache.has(url);
  }

  private clearTimeout(url: string) {
    const current = this._invalidations.get(url);
    if (current) {
      clearTimeout(current);
      this._invalidations.delete(url);
    }
  }

  private clearCachedValue(url: string) {
    this.clearTimeout(url);
    this._cache.delete(url);
  }

  private addCachedValue(url: string, value: string, timeoutMs: number) {
    this.clearCachedValue(url);
    this._cache.set(url, value);
    this._invalidations.set(
      url,
      setTimeout(() => {
        this.clearCachedValue(url);
      }, timeoutMs),
    );
  }
}
