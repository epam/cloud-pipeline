import {Button} from 'antd';
import {AppstoreFilled} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type HistoryActionProps = CommonProps & {
  historyPanelOpen?: boolean;
  onToggle?: (open: boolean) => void;
};

function HistoryAction(props: HistoryActionProps) {
  const {historyPanelOpen = false, onToggle} = props;

  return (
    <Button
      id="display-attributes"
      size="small"
      onClick={() => onToggle?.(!historyPanelOpen)}
    >
      <AppstoreFilled />
      {historyPanelOpen ? 'Hide history' : 'Show history'}
    </Button>
  );
}

export {HistoryAction};
