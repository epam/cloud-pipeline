import {Button, message} from 'antd';
import {CalendarOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type ScheduleActionProps = CommonProps & {
  configurationId?: number | string;
};

function ScheduleAction(props: ScheduleActionProps) {
  const {configurationId} = props;

  return (
    <Button
      id="configuration-schedule-button"
      size="small"
      onClick={() => message.info(`[mock] Schedule for configuration ${configurationId}`)}
    >
      <CalendarOutlined />
      Schedule
    </Button>
  );
}

export {ScheduleAction};
