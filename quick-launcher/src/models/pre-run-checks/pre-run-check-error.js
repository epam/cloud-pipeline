export default class PreRunCheckError extends Error {
  constructor(message, disclaimer) {
    super(message || 'Pre-run check error');
    this.disclaimer = disclaimer;
  }
}
