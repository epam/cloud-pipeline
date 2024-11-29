import { Badge, FlexRow, RichTextView } from '@epam/uui';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Run } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { getStatusBadgeStyle } from '../helpers';
import ActionJobFunctionFillIcon from '@epam/assets/icons/action-job_function-fill.svg?react';
import dayjs from 'dayjs';

type Props = CommonProps & {
  run: Run;
};

export const RunCard = ({ run, className, style }: Props) => {
  const { id, status, taskName, startDate } = run;

  const formattedStartDate = dayjs(startDate).format('YYYY-MM-DD, HH:mm:ss');

  return (
    <div
      className={cn('ngs-container flex flex-col', className)}
      style={style}>
      <FlexRow
        columnGap="12"
        size="24"
        alignItems="center"
        justifyContent="space-between">
        <Link
          className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline truncate"
          to={`/run/${id}`}>
          {taskName}
        </Link>

        <Badge
          caption={status}
          size="18"
          cx="shrink-0"
          {...getStatusBadgeStyle(status)}
        />
      </FlexRow>

      <div className="inline-flex">
        <Badge
          icon={ActionJobFunctionFillIcon}
          caption={'RNA-Seq Workflow'}
          color="neutral"
          size="18"
        />
      </div>

      <div>
        <RichTextView>
          <span className="text-[var(--uui-secondary-50)]">Started on:</span>{' '}
          {formattedStartDate}
        </RichTextView>
        <RichTextView>
          <span className="text-[var(--uui-secondary-50)]">
            Estimated price:{' '}
          </span>
          $0.07
        </RichTextView>
      </div>
    </div>
  );
};
