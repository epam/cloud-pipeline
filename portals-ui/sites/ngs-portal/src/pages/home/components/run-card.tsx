import { FlexRow, RichTextView } from '@epam/uui';
import cn from 'classnames';
import { Link } from 'react-router-dom';
import type { Run } from '@cloud-pipeline/core';
import { Tag } from '@cloud-pipeline/components';
import type { CommonProps } from '@cloud-pipeline/components';
import { getStatusBadgeStyle } from '../helpers';
import dayjs from 'dayjs';
import { CubeIcon } from '@heroicons/react/24/outline';

type Props = CommonProps & {
  run: Run;
};

export const RunCard = ({ run, className, style }: Props) => {
  const { id, status, taskName, startDate } = run;

  const formattedStartDate = dayjs(startDate).format('YYYY-MM-DD, HH:mm:ss');

  return (
    <div className={cn('ngs-container flex flex-col', className)} style={style}>
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

        <Tag className="shrink-0" {...getStatusBadgeStyle(status)}>
          <span className="text-xs">{status}</span>
        </Tag>
      </FlexRow>

      <div className="inline-flex">
        <Tag
          className="inline-flex items-center"
          icon={<CubeIcon className="w-4 h-4" />}>
          <span>RNA-Seq Workflow</span>
        </Tag>
      </div>

      <div className="flex flex-wrap gap-x-4">
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
