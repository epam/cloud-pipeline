import {LibraryItem} from '../../types.ts';
import {MessageOutlined} from '@ant-design/icons';
import {Button} from 'antd';

export function IssuesButton(props: {item: LibraryItem; onClick?: () => void}) {
  const {item, onClick} = props;
  return (
    <Button className={`issues-button-${item.id}`} size="small" onClick={onClick}>
      <MessageOutlined />
      {item.issuesCount > 0 && <span>{item.issuesCount}</span>}
    </Button>
  );
}
