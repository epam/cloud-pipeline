import cn from 'classnames';
import type { ReactNode } from 'react';
import { orderedStatuses, statusColors, statusIcons } from '../constants';
import type { EngineTask } from '@cloud-pipeline/core';

type Props = {
  taskName: string;
  statuses: EngineTask;
  isSelected: boolean;
  total: number;
  onTaskSelect: (taskName?: string) => void;
};

export const TaskProgress = ({ taskName, statuses, isSelected, total, onTaskSelect }: Props) => {
  const handleTaskSelect = () => {
    onTaskSelect(isSelected ? undefined : taskName);
  };

  const getProgressElements = () => {
    return orderedStatuses.reduce<ReactNode[]>((elements, status) => {
      const count = statuses[status] ?? 0;

      if (count > 0) {
        const percentage = (count / total) * 100;

        elements.push(
          <div
            key={status}
            style={{ width: `${percentage}%`, backgroundColor: statusColors[status] }}
            className="h-2 rounded-full"
          />,
        );
      }
      return elements;
    }, []);
  };

  const getIconElements = () => {
    return orderedStatuses.reduce<ReactNode[]>((elements, status, index) => {
      const count = statuses[status] ?? 0;

      if (count > 0) {
        const color = statusColors[status];
        const IconComponent = statusIcons[status];

        if (index > 0 && elements.length > 0) {
          elements.push(
            <span key={`slash-${status}`} className="mx-1">
              /
            </span>,
          );
        }

        elements.push(
          <div key={`icon-${status}`} className="flex items-center">
            <IconComponent className="w-4 h-4" style={{ color }} />
            <p>{count}</p>
          </div>,
        );
      }

      return elements;
    }, []);
  };

  return (
    <div
      className={cn('p-2 cursor-pointer hover:bg-slate-200', { 'bg-slate-200': isSelected })}
      onClick={handleTaskSelect}>
      <div className="flex justify-between items-center ">
        <h4 className={cn({ 'font-bold': isSelected })}>{taskName}</h4>

        <div className="flex">{getIconElements()}</div>
      </div>

      <div className="flex w-full gap-1">{getProgressElements()}</div>
    </div>
  );
};
