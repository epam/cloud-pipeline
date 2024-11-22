import type { AbortRequestOptions } from './@types';
import {
  createNumberIdentifierGenerator,
  AbortError,
} from '@cloud-pipeline/core';

type RequestQueueItem = {
  id: number;
  priority: number;
  unqueued: boolean;
  next?: RequestQueueItem;
  onStart(): void;
  onDone(): void;
  onAbort(): void;
};

const requestIdGenerator = createNumberIdentifierGenerator();

export default class RequestsQueue {
  private _queue: RequestQueueItem | undefined;
  private readonly _capacity: number;
  private _busyCount: number;

  /*
   * capacity - max allowed concurrent requests; "0" if queue mode is disabled
   */
  constructor(capacity = 6) {
    this._capacity = Math.max(0, capacity);
    this._busyCount = 0;
  }

  protected async queue<T>(
    action: () => Promise<T>,
    requestOptions: AbortRequestOptions,
  ): Promise<T> {
    if (this._capacity === 0) {
      return action();
    }
    const onDone = await this.addToQueue(requestOptions);
    try {
      return await action();
    } finally {
      onDone();
    }
  }

  private processQueue(): void {
    if (this._busyCount >= this._capacity) {
      return;
    }
    const next = this.next();
    if (next) {
      next.onStart();
    }
  }

  private async addToQueue(request: AbortRequestOptions): Promise<() => void> {
    const processQueue = () => {
      setTimeout(() => {
        this.processQueue();
      }, 0);
    };
    return new Promise((resolve, reject) => {
      const { requestPriority = 0, signal } = request;
      const id = requestIdGenerator();
      const onDone = () => {
        this._busyCount -= 1;
        processQueue();
      };
      const onStart = () => {
        this._busyCount += 1;
        processQueue();
        resolve(onDone);
      };
      const onAbort = () => {
        processQueue();
        reject(new AbortError());
      };
      const next: RequestQueueItem = {
        id,
        priority: requestPriority,
        unqueued: false,
        onStart,
        onAbort,
        onDone,
      };
      if (signal) {
        signal.addEventListener('abort', () => {
          if (!next.unqueued) {
            this.removeFromQueue(id);
            next.onAbort();
          }
        });
      }
      if (this._queue === undefined) {
        this._queue = next;
      } else if (this._queue.priority < requestPriority) {
        next.next = this._queue;
        this._queue = next;
      } else {
        let after = this._queue;
        while (after.next && after.next.priority >= requestPriority) {
          after = after.next;
        }
        next.next = after.next;
        after.next = next;
      }
      processQueue();
    });
  }

  private removeFromQueue(itemId: number): void {
    if (!this._queue) {
      return;
    }
    if (this._queue.id === itemId) {
      this._queue = this._queue.next;
      return;
    }
    let head: RequestQueueItem | undefined = this._queue;
    while (head?.next && head.next.id !== itemId) {
      head = head.next;
    }
    if (head?.next && head.next.id === itemId) {
      head.next = head.next?.next;
    }
  }

  private next(): RequestQueueItem | undefined {
    if (this._queue === undefined) {
      return undefined;
    }
    const next = this._queue;
    this._queue = this._queue.next;
    next.unqueued = true;
    return next;
  }
}
