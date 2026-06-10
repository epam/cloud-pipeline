import {type MouseEvent, type KeyboardEvent, useCallback, useMemo, useState} from 'react';
import {Modal, Popover, Tabs} from 'antd';
import classNames from 'classnames';
import type {MetadataValueRendererProps} from './types.ts';
import {makePrettyJson, parseJsonItems, plural, stringifyMetadataValue} from './utilities.ts';
import {getNestedRenderer} from './nested-value-renderer.tsx';

function JsonItemsTable({keys, items}: {keys: string[]; items: Record<string, unknown>[]}) {
  if (items.length === 0) {
    return null;
  }
  return (
    <table className="cp-metadata-item-json-table">
      <tbody>
        <tr>
          {keys.map((key) => (
            <th key={key}>{key}</th>
          ))}
        </tr>
        {items.map((item, index) => (
          <tr key={index}>
            {keys.map((key) => {
              const Renderer = getNestedRenderer(item[key]);
              return (
                <td key={key}>
                  <Renderer value={item[key]} />
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function preventDefault(e: MouseEvent) {
  e.stopPropagation();
  e.preventDefault();
}

function JsonRenderer(props: MetadataValueRendererProps) {
  const {className, style, value, tag} = props;
  const rawValue = stringifyMetadataValue(value) ?? '';
  const parsed = useMemo(() => parseJsonItems(rawValue), [rawValue]);
  const [expanded, setExpanded] = useState(false);
  const openModal = useCallback((event: MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setExpanded(true);
  }, []);
  const closeModal = useCallback((event: KeyboardEvent | MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setExpanded(false);
  }, []);
  if (!parsed) {
    return null;
  }
  const summary = plural(parsed.length, 'item');
  const hoverContent = (
    <div style={{maxWidth: '60vw', maxHeight: '50vh', overflow: 'auto'}}>
      <JsonItemsTable keys={parsed.keys} items={parsed.items} />
    </div>
  );
  return (
    <>
      <Popover content={hoverContent} title={tag}>
        <span
          className={classNames(className, 'underline cursor-pointer')}
          style={{
            cursor: 'pointer',
            textDecoration: 'underline',
            ...style,
          }}
          onClick={openModal}
        >
          {summary}
        </span>
      </Popover>
      <Modal
        open={expanded}
        title={tag}
        onCancel={closeModal}
        footer={null}
        width="50%"
        destroyOnHidden
      >
        <div onClick={preventDefault}>
          <Tabs
            items={[
              {
                key: 'table',
                label: 'Table',
                children: <JsonItemsTable keys={parsed.keys} items={parsed.items} />,
              },
              {
                key: 'raw',
                label: 'Raw',
                children: (
                  <pre
                    style={{
                      margin: 0,
                      maxHeight: '50vh',
                      overflow: 'auto',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                    }}
                  >
                    {makePrettyJson(rawValue)}
                  </pre>
                ),
              },
            ]}
          />
        </div>
      </Modal>
    </>
  );
}

export {JsonRenderer, JsonItemsTable};
