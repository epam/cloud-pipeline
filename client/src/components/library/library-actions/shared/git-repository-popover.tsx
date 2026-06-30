import {useCallback, useRef, useState} from 'react';
import {Button, Dropdown, Input, Popover, Row} from 'antd';
import {BranchesOutlined, DownOutlined} from '@ant-design/icons';

const CLOSE_POPOVER_DELAY_MS = 200;

type CloneType = 'https' | 'ssh';

type GitRepositoryPopoverProps = {
  https?: string;
  ssh?: string;
};

function GitRepositoryPopover({https, ssh}: GitRepositoryPopoverProps) {
  const [cloneType, setCloneType] = useState<CloneType | undefined>(undefined);
  const [visible, setVisible] = useState(false);
  const preventClosingRef = useRef(false);
  const closeTimeoutRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const availableOptions: CloneType[] = [
    ...(https ? (['https'] as CloneType[]) : []),
    ...(ssh ? (['ssh'] as CloneType[]) : []),
  ];

  const activeCloneType =
    cloneType !== undefined && availableOptions.includes(cloneType)
      ? cloneType
      : availableOptions[0];

  const onOpenChange = useCallback((open: boolean) => {
    if (!open && closeTimeoutRef.current !== undefined) {
      clearTimeout(closeTimeoutRef.current);
    }
    if (open) {
      setVisible(true);
    } else {
      closeTimeoutRef.current = setTimeout(() => {
        if (preventClosingRef.current) {
          preventClosingRef.current = false;
        } else {
          setVisible(false);
        }
      }, CLOSE_POPOVER_DELAY_MS);
    }
  }, []);

  if (availableOptions.length === 0) return null;

  const menuItems = availableOptions.map((o) => ({key: o, label: o.toUpperCase()}));

  const title = (
    <Row align="middle">
      <b style={{marginRight: 5}}>Clone repository via</b>
      <Dropdown
        menu={{
          items: menuItems,
          onClick: ({key}) => {
            preventClosingRef.current = true;
            setCloneType(key as CloneType);
          },
        }}
      >
        <a style={{lineHeight: 1}}>
          <b>
            {activeCloneType?.toUpperCase()}
            <DownOutlined />
          </b>
        </a>
      </Dropdown>
    </Row>
  );

  const content = (
    <Row>
      <Input readOnly value={activeCloneType === 'https' ? https : ssh} />
    </Row>
  );

  return (
    <Popover
      classNames={{root: 'git-repository-popover'}}
      title={title}
      content={content}
      open={visible}
      onOpenChange={onOpenChange}
      trigger={['click']}
      placement="bottomLeft"
    >
      <Button size="small" id="git-repository-button">
        <BranchesOutlined />
      </Button>
    </Popover>
  );
}

export {GitRepositoryPopover};
