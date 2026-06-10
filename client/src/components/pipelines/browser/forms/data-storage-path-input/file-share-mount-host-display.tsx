import {Row, Tooltip} from 'antd';
import {CloudRegionTag} from '../../../../shared/cloud-region-tag/cloud-region-tag.tsx';
import type {FileShareMountListItem} from './types.ts';

interface FileShareMountHostDisplayProps {
  fileShareMount: FileShareMountListItem;
  showMountType?: boolean;
}

function FileShareMountHostDisplay({
  fileShareMount,
  showMountType = false,
}: FileShareMountHostDisplayProps) {
  const mountType = showMountType && fileShareMount.mountType ? `${fileShareMount.mountType} ` : '';

  return (
    <Tooltip title={`${fileShareMount.regionName}: ${mountType}${fileShareMount.mountRoot}`}>
      <Row align="middle" style={{flexFlow: 'nowrap'}}>
        <CloudRegionTag regionId={fileShareMount.regionId} />
        <p
          style={{
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            textAlign: 'left',
          }}
        >
          : {mountType}
          {fileShareMount.mountRoot}
        </p>
      </Row>
    </Tooltip>
  );
}

export {FileShareMountHostDisplay};
