import React, {
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
} from 'react';

const MINIMUM_WIDTH = 50;

const Measurement = {
  pixels: 'pixels',
  percents: 'percents',
};

const SplitPanelContext = React.createContext({
  columns: [],
  resizerSize: 1,
  sizes: [],
  setSizes: (() => {}),
  container: React.createRef(),
  measurements: [],
});

/**
 * @typedef {object} SplitPanelOptions
 * @property {string[]} [columns]
 * @property {number[]|{[column: string]: number}} [defaultSizes]
 * @property {number} [resizerSize=1]
 */

/**
 * @param {number} panelsCount
 * @param {string[]} [columns]
 * @returns {string[]}
 */
function useCreateColumnNames(panelsCount, columns) {
  return useMemo(() => {
    const columnNames = [];
    for (let i = 0; i < (panelsCount || 0); i += 1) {
      columnNames.push(`panel-${i}`);
    }
    if (columns) {
      for (let i = 0; i < (panelsCount || 0); i += 1) {
        columnNames[i] = columns[i] || columnNames[i];
      }
    }
    return columnNames;
  }, [columns, panelsCount]);
}

/**
 * @param {number} panelsCount
 * @param {number[]|{[column: string]: number}} defaultSizes
 * @param {string[]} columnNames
 * @returns {number[]}
 */
function useDefaultSizes(panelsCount, defaultSizes, columnNames) {
  return useMemo(() => {
    const sizes = [];
    for (let i = 0; i < (panelsCount || 0); i += 1) {
      const columnName = columnNames && Array.isArray(columnNames)
        ? columnNames[i]
        : undefined;
      let defaultSize;
      if (
        typeof defaultSizes === 'object'
        && columnName
        && Object.prototype.hasOwnProperty.call(defaultSizes, columnName)
      ) {
        defaultSize = defaultSizes[columnName];
      } else if (defaultSizes && Array.isArray(defaultSizes)) {
        defaultSize = defaultSizes[i];
      }
      sizes.push(defaultSize);
    }
    return sizes;
  }, [
    defaultSizes,
    columnNames,
    panelsCount,
  ]);
}

/**
 * @param {number} panelsCount
 * @param {SplitPanelOptions} [options]
 * @returns {{setSizes: function, sizes: number[], resizerSize: number, container: React.Ref}}
 */
function useCreateSplitPanelContext(panelsCount, options = {}) {
  const {
    defaultSizes,
    resizerSize,
    columns,
  } = options;
  const columnNames = useCreateColumnNames(panelsCount, columns);
  const defaultSizeArray = useDefaultSizes(panelsCount, defaultSizes, columnNames);
  const measurements = useMemo(
    () => defaultSizeArray.map((o) => (typeof o === 'number' ? Measurement.pixels : Measurement.percents)),
    [defaultSizeArray],
  );
  const [sizes, setSizes] = useState(defaultSizeArray);
  const containerRef = useRef();
  return useMemo(() => ({
    columns: columnNames,
    measurements,
    sizes,
    setSizes,
    resizerSize: resizerSize || 1,
    container: containerRef,
  }), [
    columnNames,
    measurements,
    sizes,
    setSizes,
    resizerSize,
    containerRef,
  ]);
}

function usePanelSizes() {
  const { sizes = [] } = useContext(SplitPanelContext);
  return sizes || [];
}

function convertSizeToGridSize(size, minimum) {
  if (
    typeof size === 'number'
    || !Number.isNaN(Number(size))
  ) {
    // pixels
    return `${Number(size)}px`;
  }
  const getFr = (value) => {
    if (minimum) {
      return `minmax(${minimum}px, ${value}fr)`;
    }
    return `${value}fr`;
  };
  if (typeof size === 'string') {
    const [, value] = size.match(/^(.+)\*$/) || [];
    if (value && !Number.isNaN(Number(value))) {
      return getFr(value);
    }
  }
  return getFr(1);
}

function usePanelResizerSize() {
  const { resizerSize = 1 } = useContext(SplitPanelContext);
  return resizerSize;
}

function useColumns() {
  const { columns } = useContext(SplitPanelContext);
  return columns;
}

function getColumnKey(column, columns) {
  if (typeof column === 'number') {
    let columnCorrected = column;
    if (columns.length > 0) {
      columnCorrected = column - Math.floor(column / columns.length) * columns.length;
    }
    return columns[columnCorrected];
  }
  return column;
}

function useColumnKey(column) {
  const columns = useColumns();
  return useMemo(() => getColumnKey(column, columns), [column, columns]);
}

function useColumnsSpan(start, end) {
  const startColumn = useColumnKey(start);
  const endColumn = useColumnKey(end);
  const columns = useColumns();
  return useMemo(() => {
    const s = (columns || []).indexOf(startColumn);
    const e = (columns || []).indexOf(endColumn);
    if (s >= 0 && e >= 0) {
      return Math.abs(e - s) * 2 + 1;
    }
    return 1;
  }, [columns, startColumn, endColumn]);
}

function getResizerKey(column, columns) {
  const columnName = getColumnKey(column, columns);
  const idx = columns.indexOf(columnName);
  if (idx === -1 || idx === columns.length - 1) {
    return undefined;
  }
  return (idx + 1) * 2; // [panel-0] 2 [panel-1] 4 ...
}

/**
 * @param {number|string} column - column index or name at the left to the resizer
 * @returns {number|string} - resizer grid column name
 */
function useColumnResizerKey(column) {
  const { columns } = useContext(SplitPanelContext);
  return useMemo(() => getResizerKey(column, columns), [column, columns]);
}

/**
 * @param {number|string} column - column index or name at the left to the resizer
 * @returns {[number|string, number|string]} - array of the [left, right] grid column names
 */
function useResizerAffectedColumnsKeys(column) {
  const { columns } = useContext(SplitPanelContext);
  const columnName = useColumnKey(column);
  return useMemo(() => {
    const idx = columns.indexOf(columnName);
    if (idx === -1 || idx === columns.length - 1) {
      return [];
    }
    return [columns[idx], columns[idx + 1]];
  }, [columnName, columns]);
}

function sizeMapper(info) {
  const {
    key,
    size = 'auto',
  } = info;
  const getSize = () => {
    if (
      typeof size === 'number'
      || !Number.isNaN(Number(size))
    ) {
      return `${size}px`;
    }
    return size;
  };
  if (!key) {
    return getSize();
  }
  return `[${key}] ${getSize()}`;
}

function getTemplate(elements = []) {
  if (!elements || !elements.length) {
    return undefined;
  }
  return elements.reduce((result, element) => {
    if (!result.length) {
      return [{
        presentation: sizeMapper(element),
        repeat: 1,
        key: element.key,
        size: element.size,
      }];
    }
    const last = result[result.length - 1];
    if (!last.key && !element.key && last.size === element.size) {
      last.repeat += 1;
      return [
        ...result.slice(0, -1),
        {
          ...last,
          presentation: `repeat(${last.repeat}, ${sizeMapper({ size: last.size })})`,
        },
      ];
    }
    return [
      ...result,
      {
        presentation: sizeMapper(element),
        repeat: 1,
        key: element.key,
        size: element.size,
      },
    ];
  }, []).map((o) => o.presentation).join(' ');
}

/**
 * @returns {{size: string, key: string?}[]}
 */
function usePanelGridColumnSizes() {
  const sizes = usePanelSizes();
  const resizer = usePanelResizerSize();
  const columns = useColumns();
  const getColumn = useCallback((column) => getColumnKey(column, columns), [columns]);
  return useMemo(
    () => ((sizes || [])
      .map((aSize, index) => ([
        { key: getColumn(index), size: convertSizeToGridSize(aSize, MINIMUM_WIDTH) },
        { size: convertSizeToGridSize(resizer) },
      ]))
      .reduce((r, c) => ([...r, ...c]), [])
      .slice(0, -1)),
    [sizes, resizer, getColumn],
  );
}

function useGridStyle(rows) {
  const columns = usePanelGridColumnSizes();

  return useMemo(() => ({
    display: 'grid',
    gridTemplateColumns: getTemplate(columns),
    gridTemplateRows: getTemplate(rows),
  }), [columns, rows]);
}

/**
 * Returns callback for resizer
 * @param {number|string} column - column index or name at the left to the resizer
 * @param {function} [converter]
 * @returns {function(left: number, right: number)}
 */
function useResizerChangeSizesCallback(column, converter) {
  const { setSizes } = useContext(SplitPanelContext);
  const { columns } = useContext(SplitPanelContext);
  const columnName = useColumnKey(column);
  return useCallback((leftSize, rightSize) => {
    const idx = columns.indexOf(columnName);
    if (idx === -1 || idx === columns.length - 1) {
      return;
    }
    setSizes((currentSizes) => {
      const newSizes = [...(currentSizes || [])];
      newSizes[idx] = leftSize;
      newSizes[idx + 1] = rightSize;
      if (typeof converter === 'function') {
        return converter(newSizes);
      }
      return newSizes;
    });
  }, [columnName, columns, setSizes, converter]);
}

/**
 * Converts sizes in pixels to pixels / percent sizes according to its measurements
 * @returns {function(newSizes: *[])}
 */
function useConvertSizes() {
  const { container, measurements, resizerSize } = useContext(SplitPanelContext);
  return useCallback((sizesToConvert = []) => {
    if (!sizesToConvert || sizesToConvert.length === 0) {
      return undefined;
    }
    if (!measurements.some((measurement) => measurement === Measurement.percents)) {
      return undefined;
    }
    if (
      container.current
      && container.current.clientWidth
    ) {
      const getFragments = (o) => {
        const [, value = '1'] = (o || '').match(/(.+)\*$/) || [];
        return Number(value);
      };
      const clientWidth = container.current.clientWidth - (measurements.length - 1) * resizerSize;
      const sizeForFragments = clientWidth - sizesToConvert
        .filter((aSize, index) => typeof aSize === 'number' && measurements[index] === Measurement.pixels)
        .reduce((r, c) => r + c, 0);
      const touchedFragmentsElements = sizesToConvert
        .filter((aSize, index) => typeof aSize === 'number' && measurements[index] === Measurement.percents);
      const pixelsToFragmentsTotal = touchedFragmentsElements
        .reduce((r, c) => r + c, 0);
      const untouchedFragments = sizesToConvert
        .filter((aSize) => typeof aSize !== 'number')
        .map(getFragments)
        .reduce((r, c) => r + c, 0);
      const untouchedFragmentsSize = sizeForFragments - pixelsToFragmentsTotal;
      const touchedFragments = untouchedFragments > 0 && untouchedFragmentsSize > 0
        ? (untouchedFragments / untouchedFragmentsSize) * pixelsToFragmentsTotal
        : (touchedFragmentsElements.length || 1);
      const fragmentSize = pixelsToFragmentsTotal / touchedFragments;
      return sizesToConvert.map((aSize, index) => {
        const measurement = measurements[index];
        switch (measurement) {
          case Measurement.percents: {
            if (typeof aSize === 'number') {
              // we need to convert pixels to percents
              if (fragmentSize === 0) {
                return '1*';
              }
              return `${aSize / fragmentSize}*`;
            }
            return aSize;
          }
          case Measurement.pixels:
          default:
            // normally, aSize should be a number or undefined
            if (typeof aSize === 'string') {
              return undefined;
            }
            return aSize;
        }
      });
    }
    return undefined;
  }, [container, measurements, resizerSize]);
}

/**
 * @param {number|string} column - column index or name at the left to the resizer
 * @returns {function(number, number)}
 */
function useResizerCommitSizesCallback(column) {
  const converter = useConvertSizes();
  return useResizerChangeSizesCallback(column, converter);
}

export {
  useCreateSplitPanelContext,
  useGridStyle,
  useResizerChangeSizesCallback,
  useResizerCommitSizesCallback,
  useColumnResizerKey,
  useColumnKey,
  useResizerAffectedColumnsKeys,
  useColumnsSpan,
  SplitPanelContext,
  MINIMUM_WIDTH,
};
