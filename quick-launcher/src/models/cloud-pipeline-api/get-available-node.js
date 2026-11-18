import getNodes from './nodes';
import fetchSettings from '../base/settings';

export default function getAvailableNode() {
  return new Promise((resolve, reject) => {
    fetchSettings()
      .then(settings => {
        getNodes()
          .then(nodes => {
            const [node] = nodes.filter(node => node.runId
              && node.labels
              && node.labels.hasOwnProperty(settings.tag)
              && settings.tagValueRegExp.test(node.labels[settings.tag])
            );
            resolve(node);
          })
          .catch(reject);
      });
  });
}
