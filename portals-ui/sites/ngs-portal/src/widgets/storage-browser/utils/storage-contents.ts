import type { DataStorageItem } from '@cloud-pipeline/core';
import { fetchDataStoragePage } from '@cloud-pipeline/api';
import { ROOT_PLACEHOLDER } from './navigation.ts';

export type StorageContentsPage = {
  page: number;
  items: DataStorageItem[];
};

type StorageContentsPageWithMarker = StorageContentsPage & {
  marker: string | undefined;
};

export type StorageContents = {
  storageId: number;
  path?: string;
  pending: boolean;
  error: string | undefined;
  pages: StorageContentsPage[];
  items: DataStorageItem[];
  hasMoreItems: boolean;
};

export type StorageDataListener = (data: StorageContents) => void;

export type StorageContentsLoaderOptions = {
  storageId: number;
  path?: string;
  pageSize?: number;
  listeners?: StorageDataListener[];
  showVersions?: boolean;
  showArchived?: boolean;
};

function pageWithMarkerToPage(pageWithMarker: StorageContentsPageWithMarker): StorageContentsPage {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { marker, ...page } = pageWithMarker;
  return page;
}

export class StorageContentsLoader {
  private listeners: StorageDataListener[];
  private readonly storageId: number;
  private readonly path: string | undefined;
  private readonly pageSize: number;
  private readonly showVersions: boolean;
  private readonly showArchived: boolean;
  private pages: StorageContentsPageWithMarker[];
  private fetchToken: unknown;
  private abortController: AbortController;
  private pending: boolean;
  private error: string | undefined;

  constructor(options: StorageContentsLoaderOptions) {
    const { storageId, path, pageSize = 100, listeners = [], showVersions = false, showArchived = false } = options;
    this.storageId = storageId;
    this.path = path;
    this.pageSize = pageSize;
    this.showVersions = showVersions;
    this.showArchived = showArchived;
    this.listeners = listeners.slice();
    this.pending = true;
    this.error = undefined;
    this.pages = [];
    this.abortController = new AbortController();
    this.fetchToken = {};
    void this.fetchNextPage();
  }

  abort() {
    this.fetchToken = {};
    this.abortController.abort();
    this.abortController = new AbortController();
  }

  destroy() {
    this.abort();
    this.listeners = [];
  }

  addListener(listener: StorageDataListener) {
    this.removeListener(listener);
    this.listeners.push(listener);
  }

  removeListener(listener: StorageDataListener) {
    this.listeners = this.listeners.filter((l) => l !== listener);
  }

  getData(): StorageContents {
    const lastPage = this.pages.length > 0 ? this.pages[this.pages.length - 1] : undefined;
    return {
      storageId: this.storageId,
      path: this.path,
      pending: this.pending,
      error: this.error,
      hasMoreItems: lastPage?.marker !== undefined && this.error !== undefined && !this.pending,
      pages: this.pages.map(pageWithMarkerToPage),
      items: this.pages.reduce<DataStorageItem[]>((res, page) => res.concat(page.items), []),
    };
  }

  async reload() {
    this.abort();
    this.pending = true;
    this.error = undefined;
    this.pages = [];
    this.report();
    return this.fetchNextPage();
  }

  async fetchNextPage() {
    this.abort();
    const { signal } = this.abortController;
    const lastPage = this.pages.length > 0 ? this.pages[this.pages.length - 1] : undefined;
    if (lastPage && !lastPage.marker) {
      // no more pages
      this.pending = false;
      this.error = undefined;
      this.report();
      return;
    }
    const marker = lastPage?.marker;
    const token = (this.fetchToken = {});
    const commit = (fn: () => void): void => {
      if (token === this.fetchToken && !signal.aborted) {
        fn();
        this.report();
      }
    };
    commit(() => {
      this.pending = true;
      this.error = undefined;
    });
    try {
      const pageData = await fetchDataStoragePage({
        id: this.storageId,
        path: this.path === ROOT_PLACEHOLDER ? undefined : this.path,
        marker,
        showVersion: this.showVersions,
        showArchived: this.showArchived,
        pageSize: this.pageSize,
      });
      const nextPage: StorageContentsPageWithMarker = {
        marker: pageData.nextPageMarker,
        items: pageData.results ?? [],
        page: this.pages.length,
      };
      commit(() => {
        this.pending = false;
        this.error = undefined;
        this.pages.push(nextPage);
      });
    } catch (error) {
      commit(() => {
        this.pending = false;
        this.error = error instanceof Error ? error.message : 'Error fetching page';
      });
    }
  }

  protected report() {
    const data = this.getData();
    for (const listener of this.listeners) {
      listener(data);
    }
  }
}
