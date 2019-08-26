import Remote from './base';

export const Statuses = {
  pending: 'pending',
  success: 'success',
  running: 'running',
  failure: 'failure',
  cancelled: 'cancelled',
};

export default class Status extends Remote {
  constructor(id) {
    super();
    this.url = `/status/${id}`;
  }
}
