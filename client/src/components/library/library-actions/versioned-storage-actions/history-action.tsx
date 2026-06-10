import {Button, message} from 'antd';
import {AppstoreFilled} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type HistoryActionProps = CommonProps & {
  storageId?: number | string;
  historyPanelOpen?: boolean;
  onToggle?: (open: boolean) => void;
};

function HistoryAction(props: HistoryActionProps) {
  const {storageId, historyPanelOpen = false, onToggle} = props;

  return (
    <Button
      id="display-attributes"
      size="small"
      onClick={() => {
        const next = !historyPanelOpen;
        message.info(`[mock] ${next ? 'Show' : 'Hide'} history for versioned storage ${storageId}`);
        onToggle?.(next);
      }}
    >
      <AppstoreFilled />
      {historyPanelOpen ? 'Hide history' : 'Show history'}
    </Button>
  );
}

export {HistoryAction};
