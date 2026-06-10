import {Popover} from 'antd';
import classNames from 'classnames';
import type {ReactElement} from 'react';
import type {CommonProps} from '../../../../@types/common.ts';

type NestedValueRendererProps = CommonProps & {
  value: unknown;
};

type NestedValueRenderer = (props: NestedValueRendererProps) => ReactElement | null;

function renderPrimitive(value: unknown) {
  if (value === undefined || value === null) {
    return '';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function DefaultNestedRenderer(props: NestedValueRendererProps) {
  const {className, style, value} = props;
  return (
    <span className={className} style={style}>
      {renderPrimitive(value)}
    </span>
  );
}

function getNestedRenderer(value: unknown): NestedValueRenderer {
  if (value && Array.isArray(value)) {
    return ArrayRenderer;
  }
  if (value && typeof value === 'object') {
    return ObjectRenderer;
  }
  return DefaultNestedRenderer;
}

function ArrayRenderer(props: NestedValueRendererProps) {
  const {className, value} = props;
  const items = Array.isArray(value) ? value : [];
  return (
    <div className={className}>
      {items.map((item, index) => {
        const Renderer = getNestedRenderer(item);
        return <Renderer key={index} value={item} />;
      })}
    </div>
  );
}

function ObjectRenderer(props: NestedValueRendererProps) {
  const {className, value} = props;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return <span className={className}>{renderPrimitive(value)}</span>;
  }
  const record = value as Record<string, unknown>;
  const keys = Object.keys(record);
  let identifier: string | undefined;
  let name: string | undefined;
  const details = keys.map((key) => {
    const itemValue = record[key];
    if (/^id$/i.test(key) && (typeof itemValue === 'string' || typeof itemValue === 'number')) {
      identifier = `#${itemValue}`;
    }
    if (/^name$/i.test(key) && typeof itemValue === 'string') {
      name = itemValue;
    }
    return {
      key,
      value: itemValue,
      Renderer: getNestedRenderer(itemValue),
    };
  });
  return (
    <Popover
      title={undefined}
      content={
        details.length === 0 ? (
          'Empty object'
        ) : (
          <table>
            <tbody>
              {details.map((detail) => {
                const Renderer = detail.Renderer;
                return (
                  <tr key={detail.key}>
                    <th>{detail.key}:</th>
                    <td>
                      <Renderer value={detail.value} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )
      }
    >
      <span
        className={classNames(className, 'underline cursor-pointer')}
        style={{
          fontStyle: 'italic',
          cursor: 'pointer',
          textDecoration: 'underline',
        }}
      >
        {name || identifier || 'Object'}
      </span>
    </Popover>
  );
}

export {ArrayRenderer, DefaultNestedRenderer, ObjectRenderer, getNestedRenderer};
export type {NestedValueRendererProps};
