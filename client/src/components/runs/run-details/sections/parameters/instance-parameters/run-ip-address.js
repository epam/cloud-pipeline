import React from 'react';
import {Link} from 'react-router';
import {generateRunInstanceParameterValueComponent} from './common';

const RunIpAddress = generateRunInstanceParameterValueComponent(
  'nodeIP',
  {
    render: (nodeIp, run) => {
      const {startDate, endDate, instance} = run || {};
      if (!nodeIp || !instance) {
        return null;
      }
      const parts = [
        startDate && `from=${encodeURIComponent(startDate)}`,
        endDate && `to=${encodeURIComponent(endDate)}`
      ].filter(Boolean);
      const query = parts.length > 0 ? `?${parts.join('&')}` : '';
      const isWindowsRun = /^windows$/i.test(run.platform);
      const nodeIpParts = nodeIp.split('.');
      let url = nodeIpParts.length === 4
        ? `/cluster/ip-${nodeIpParts.join('-')}/${isWindowsRun ? 'info' : `monitor${query}`}`
        : undefined;
      let content = nodeIp;
      if (instance && instance.nodeName) {
        url = `/cluster/${instance.nodeName}/${isWindowsRun ? 'info' : `monitor${query}`}`;
        content = `${instance.nodeName} (${nodeIp})`;
      }
      if (url) {
        return (
          <Link to={url}>
            {content}
          </Link>
        );
      }
      return content;
    }
  }
);

export default RunIpAddress;
