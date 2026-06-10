import {CommonProps} from '../../@types/common.ts';
import {ReactNode} from 'react';
import classNames from 'classnames';
import {CloseOutlined} from '@ant-design/icons';

function LayoutPanel(
  props: CommonProps & {
    title: ReactNode;
    children: ReactNode;
    onClose?: () => void;
  },
) {
  const {className, style, title, onClose, children} = props;
  return (
    <div className={classNames(className, 'flex', 'flex-col')} style={style}>
      <div
        key="panel-title"
        className="shrink-0 px-1 py-0.5 w-full flex items-center bg-elevated-header border-b border-card-border"
      >
        <div className="flex-1 truncate">{title}</div>
        {onClose && <CloseOutlined onClick={onClose} />}
      </div>
      <div className="w-full flex-1 overflow-auto">{children}</div>
    </div>
  );
}

export {LayoutPanel};
