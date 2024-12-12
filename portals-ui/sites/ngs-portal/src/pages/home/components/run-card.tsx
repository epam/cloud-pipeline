import { Link } from 'react-router-dom';
import cn from 'classnames';
import { Typography } from 'antd';
import { displayDate, type Run } from '@cloud-pipeline/core';
import { Tag } from '@cloud-pipeline/components';
import type { CommonProps } from '@cloud-pipeline/components';
import { getStatusBadgeStyle } from '../helpers';
import { CubeIcon } from '@heroicons/react/24/outline';

type Props = CommonProps & {
  run: Run;
};

export const RunCard = ({ run, className, style }: Props) => {
  const { id, status, taskName, startDate } = run;

  return (
    <div className={cn('ngs-container flex flex-col', className)} style={style}>
      <div className="flex flex-nowrap justify-between items-center">
        <Link
          className="text-[var(--uui-link)] hover:text-[var(--uui-link-hover)] no-underline truncate"
          to={`/run/${id}`}>
          {taskName}
        </Link>
        <Tag className="shrink-0" {...getStatusBadgeStyle(status)}>
          <span className="text-xs">{status}</span>
        </Tag>
      </div>

      <div className="inline-flex">
        <Tag
          className="inline-flex items-center"
          icon={<CubeIcon className="w-4 h-4" />}>
          <span>RNA-Seq Workflow</span>
        </Tag>
      </div>
      <div className="flex flex-wrap gap-x-4 text-sm">
        <Typography>
          <span className="text-faded">Started on: </span>
          {displayDate(startDate)}
        </Typography>
        <Typography>
          <span className="text-faded">Estimated price: </span>
          $0.07
        </Typography>
      </div>
    </div>
  );
};
