import {Button, message} from 'antd';

import type {CommonProps} from '../../../../@types/common.ts';

type GenerateReportActionProps = CommonProps & {
  storageId?: number | string;
};

function GenerateReportAction(props: GenerateReportActionProps) {
  const {storageId} = props;

  return (
    <Button
      size="small"
      type="primary"
      onClick={() => message.info(`[mock] Generate report for versioned storage ${storageId}`)}
    >
      Generate report
    </Button>
  );
}

export {GenerateReportAction};
