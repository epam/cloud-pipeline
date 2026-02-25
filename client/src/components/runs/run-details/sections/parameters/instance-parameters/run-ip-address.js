import React from 'react';
import {
  Link} from 'react-router';
import {generateRunInstanceParameterValueComponent} from './common';
import {
  CP_CAP_REQUESTS_CPU,
  CP_CAP_REQUESTS_GPU,
  CP_CAP_REQUESTS_RAM
} from '../../../../../pipelines/launch/form/utilities/parameters';
import {Dropdown,
  Menu
} from 'antd';
import { DownOutlined } from '@ant-design/icons';

const RunIpAddress = generateRunInstanceParameterValueComponent(
  'nodeIP',
  {
    render: (nodeIp, run, routing) => {
      const {id: runId, startDate, endDate, instance, pipelineRunParameters = []} = run || {};
      if (!nodeIp || !instance) {
        return null;
      }
      const isCapacityBlocksRun = (pipelineRunParameters || []).some((pr) => [
        CP_CAP_REQUESTS_CPU,
        CP_CAP_REQUESTS_GPU,
        CP_CAP_REQUESTS_RAM
      ].includes(pr.name));
      const parts = [
        startDate && `from=${encodeURIComponent(startDate)}`,
        endDate && `to=${encodeURIComponent(endDate)}`
      ].filter(Boolean);
      const isWindowsRun = /^windows$/i.test(run.platform);
      const nodeIpParts = nodeIp.split('.');
      let baseUrl = nodeIpParts.length === 4
        ? `/cluster/ip-${nodeIpParts.join('-')}/${isWindowsRun ? 'info' : 'monitor'}`
        : undefined;
      let content = nodeIp;
      if (instance && instance.nodeName) {
        baseUrl = `/cluster/${instance.nodeName}/${isWindowsRun ? 'info' : 'monitor'}`;
        content = `${instance.nodeName} (${nodeIp})`;
      }
      const urls = [];
      if (baseUrl) {
        const buildUrl = (queryStringParts = []) => queryStringParts.length > 0
          ? `${baseUrl}?${queryStringParts.join('&')}` : baseUrl;
        if (isCapacityBlocksRun) {
          urls.push({
            key: 'run statistics',
            title: <span><b>Run</b> statistics</span>,
            url: buildUrl(parts.concat(`runId=${runId}`))
          });
        }
        urls.push({
          key: 'node statistics',
          title: <span><b>Node</b> statistics</span>,
          url: buildUrl(parts)
        });
      }
      if (urls.length > 1) {
        const onNavigate = ({key}) => {
          const urlConfig = urls.find((u) => u.key === key);
          if (urlConfig) {
            routing.push(urlConfig.url);
          }
        };
        const menu = (
          <Menu onClick={onNavigate}>
            {
              urls.map(url => (
                <Menu.Item key={url.key}>
                  <Link to={url.url} onClick={event => event.preventDefault()}>
                    {url.title}
                  </Link>
                </Menu.Item>
              ))
            }
          </Menu>
        );
        console.log(menu);
        return (
          <Dropdown overlay={menu}>
            <a>
              {content} <DownOutlined />
            </a>
          </Dropdown>
        );
      }
      if (urls.length > 0) {
        return (
          <Link to={urls[0].url}>
            {content}
          </Link>
        );
      }
      return content;
    }
  }
);

export default RunIpAddress;
