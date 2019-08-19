import dateFns from 'date-fns';
import {Remote} from './base';

export const Statuses = {
  pending: 'pending',
  success: 'success',
  running: 'running',
  failure: 'failure',
  cancelled: 'cancelled',
};

export default class Status extends Remote {
  constructor(id) {
    super(`STATUS_${id}`, {status: Statuses.pending});
    this.url = `/status/${id}`;
    // todo: remove!
    this.fetchIndex = 0;
  }

  // todo: remove!
  async fetch() {
    this.fetchIndex += 1;
    return super.fetch();
  }

  // todo: remove!
  update(value) {
    super.update(value);
    if (this.loaded && this.value.status === Statuses.pending) {
      if (this.fetchIndex > 6) {
        this.value.status = Statuses.success;
        this.value.result = {
          expires: dateFns.format(new Date(), 'YYYY-MM-DD HH:mm:ss.SSS'),
          url: 'download url',
        };
      } else if (this.fetchIndex > 3) {
        this.value.status = Statuses.running;
      }
    }
  }
}
