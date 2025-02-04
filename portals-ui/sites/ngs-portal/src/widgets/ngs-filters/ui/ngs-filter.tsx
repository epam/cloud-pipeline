import { useCallback, useMemo } from 'react';
import type { CommonProps } from '@cloud-pipeline/components';
import { isUserInfo, userMatchesCriteria } from '@cloud-pipeline/core';
import type {
  NgsFilterProps,
  NgsItemsTagFilterConfiguration,
  NgsItemsTagFilterValue,
} from '../types.ts';
import { NGS_ITEMS_OWNER_FILTER } from '../types.ts';
import { excludeFilter } from '../helpers/filter-items.ts';
import classNames from 'classnames';
import { Select, Tooltip } from 'antd';
import { NgsUserCard } from '../../cards';

type Props = CommonProps &
  Omit<NgsFilterProps, 'config'> & {
    filter: NgsItemsTagFilterConfiguration;
  };

export const NgsFilter = ({
  className,
  style,
  filters,
  onFiltersChange,
  filter: filterConfig,
}: Props) => {
  const { key, title, values } = filterConfig;

  const handleFilterChange = useCallback(
    (values?: string[]) => {
      const filterValues = values ?? [];
      if (onFiltersChange) {
        const excluded = excludeFilter(filters, key);
        onFiltersChange(
          filterValues.length === 0
            ? excluded
            : { [filterConfig.key]: filterValues, ...(excluded ?? {}) },
        );
      }
    },
    [onFiltersChange, filterConfig, filters, key],
  );

  const selectedValues = useMemo(
    () => (filters && key in filters ? filters[key] : []),
    [filters, key],
  );

  const filterOption = useCallback(
    (inputValue: string, option?: NgsItemsTagFilterValue) => {
      if (!option) {
        return false;
      }
      if (
        key.toLowerCase() === NGS_ITEMS_OWNER_FILTER.toLowerCase() &&
        isUserInfo(option.data)
      ) {
        return userMatchesCriteria(option.data, inputValue, true);
      }
      return option.value
        .toLowerCase()
        .includes(inputValue.toLowerCase().trim());
    },
    [key],
  );

  const options = useMemo(
    () =>
      values.map((val) => ({
        ...val,
        disabled: val.count === 0,
      })),
    [values],
  );

  return (
    <div
      className={classNames(className, 'flex items-center space-x-1')}
      style={style}>
      <Select
        mode="multiple"
        style={{ width: '100%', minWidth: '200px' }}
        options={options}
        optionRender={(opt) => (
          <div className="flex items-center justify-between">
            {key.toLowerCase() === NGS_ITEMS_OWNER_FILTER ? (
              <NgsUserCard
                userName={opt.data.value}
                showIcon={false}
                showTooltip={false}
              />
            ) : (
              <span>{opt.data.value}</span>
            )}
            <span className="mr-1">{opt.data.count}</span>
          </div>
        )}
        labelRender={(opt) =>
          key.toLowerCase() === NGS_ITEMS_OWNER_FILTER &&
          typeof opt.value === 'string' ? (
            <NgsUserCard userName={opt.value} showIcon={false} />
          ) : (
            <span>{opt.value}</span>
          )
        }
        placeholder="Select..."
        maxTagCount={1}
        prefix={title ? `${title}:` : undefined}
        value={selectedValues}
        onChange={handleFilterChange}
        maxTagPlaceholder={(omittedValues) => (
          <Tooltip
            styles={{ root: { pointerEvents: 'none' } }}
            title={omittedValues
              .map(({ label }) => (typeof label === 'string' ? label : false))
              .filter(Boolean)
              .join(', ')}>
            <span>+{selectedValues.length - 1}</span>
          </Tooltip>
        )}
        filterOption={filterOption}
      />
    </div>
  );
};
