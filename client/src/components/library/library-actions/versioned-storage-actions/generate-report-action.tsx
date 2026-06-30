import {useState} from 'react';
import {Button, message} from 'antd';
import {useQuery} from '@tanstack/react-query';

import type {CommonProps} from '../../../../@types/common.ts';
import {pipelineQueryOptions} from '../../../../queries/pipelines/pipeline.ts';
import GenerateReportDialog from '../../../pipelines/browser/versioned-storage/dialogs/generate-report';
import PipelineGenerateReport from '../../../../models/pipelines/PipelineGenerateReport';
import {downloadBlob} from '../../../../utils/download-blob';

type GenerateReportActionProps = CommonProps & {
  storageId?: number | string;
};

type ReportSettings = {
  authors?: string[];
  extensions?: string[];
  dateFrom?: string;
  dateTo?: string;
  includeDiff?: boolean;
  splitDiffsBy?: string;
  downloadAsArchive?: boolean;
};

function checkForBlobErrors(blob: Blob): Promise<string | false> {
  return new Promise((resolve) => {
    const fr = new FileReader();
    fr.onload = function () {
      try {
        const json = JSON.parse(this.result as string);
        const {status, message: msg} = json ?? {};
        if (/^error$/i.test(status)) {
          resolve(msg || 'Error downloading file');
          return;
        }
      } catch (_) {}
      resolve(false);
    };
    fr.readAsText(blob);
  });
}

function GenerateReportAction(props: GenerateReportActionProps) {
  const {storageId} = props;
  const [dialogOpen, setDialogOpen] = useState(false);

  const numericId = storageId !== undefined ? Number(storageId) : undefined;
  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId, {enabled: numericId !== undefined}));

  const handleGenerate = async (settings: ReportSettings = {}) => {
    if (numericId === undefined) return;
    const {authors, extensions, dateFrom, dateTo, includeDiff, splitDiffsBy, downloadAsArchive} = settings;
    const hide = message.loading('Generating report...', 0);
    try {
      const request = new PipelineGenerateReport(numericId);
      await request.send({
        commitsFilter: {authors, extensions, dateFrom, dateTo},
        includeDiff,
        groupType: splitDiffsBy,
        archive: downloadAsArchive,
        userTimeOffsetInMin: -new Date().getTimezoneOffset(),
      });
      if (request.error) {
        message.error(request.error || 'Error downloading file', 5);
      } else if (request.value instanceof Blob) {
        const fileName = `${pipeline?.name ?? 'report'}-report`;
        const extension = downloadAsArchive ? 'zip' : 'docx';
        if (request.value.type?.includes('application/json')) {
          void checkForBlobErrors(request.value).then((error) => {
            if (error) {
              void message.error(error, 5);
            } else {
              downloadBlob(request.value, `${fileName}.${extension}`);
            }
          });
        } else {
          downloadBlob(request.value, `${fileName}.${extension}`);
        }
      } else {
        message.error('Error downloading file', 5);
      }
    } finally {
      hide();
    }
    setDialogOpen(false);
  };

  return (
    <>
      <Button
        size="small"
        type="primary"
        onClick={() => setDialogOpen(true)}
      >
        Generate report
      </Button>
      <GenerateReportDialog
        visible={dialogOpen}
        onCancel={() => setDialogOpen(false)}
        onOk={handleGenerate}
      />
    </>
  );
}

export {GenerateReportAction};
