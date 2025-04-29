import Remote from '../basic/Remote';

/**
 * @typedef {Object} FetchPluginsOptions
 * @property {string | number} [toolId]
 * @property {string | number} [pipelineId]
 * @property {string} [version]
 */

/**
 */
export default class FilterPlugins extends Remote {
  /**
   * @param {FetchPluginsOptions} options
   */
  constructor (options) {
    super();
    const {
      toolId,
      pipelineId,
      version
    } = options;
    const q = [];
    if (toolId !== undefined) {
      q.push(`toolId=${encodeURIComponent(toolId)}`);
    }
    if (pipelineId !== undefined) {
      q.push(`pipelineId=${encodeURIComponent(pipelineId)}`);
    }
    if (version !== undefined) {
      q.push(`version=${encodeURIComponent(version)}`);
    }
    if (q.length > 0) {
      this.url = `/plugins/assign?${q.join('&')}`;
    } else {
      this.url = '/plugins/assign';
    }
  };
}
