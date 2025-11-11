// eslint-disable-next-line max-len
const DEFAULT_FONT_FAMILY = '-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,\'Helvetica Neue\',Arial,\'Noto Sans\',sans-serif,\'Apple Color Emoji\',\'Segoe UI Emoji\',\'Segoe UI Symbol\',\'Noto Color Emoji\'';
const DEFAULT_FONT_SIZE_PT = 8;

const dpr = window.devicePixelRatio;

/**
 * @typedef {Object} LabelSizerOptions
 * @property {CanvasRenderingContext2D} [textCanvasContext]
 * @property {string} [fontSize]
 * @property {string} [fontFamily]
 * @property {boolean} [verticalAxis=false]
 */

/**
 * @param {LabelSizerOptions} options
 * @returns {{getLabelSize: (function(label:string):number), releaseContext: (function:void)}}
 */
function getLabelSizer (options) {
  const {
    textCanvasContext,
    fontSize = DEFAULT_FONT_SIZE_PT,
    fontFamily = DEFAULT_FONT_FAMILY,
    verticalAxis = false
  } = options || {};
  const getLabelSize = (label) => {
    let width, height;
    if (textCanvasContext) {
      const measurement = textCanvasContext.measureText(label);
      width = measurement.width;
      height = measurement.fontBoundingBoxAscent + measurement.fontBoundingBoxDescent;
    }
    return (verticalAxis ? height : width);
  };
  if (textCanvasContext) {
    textCanvasContext.save();
    textCanvasContext.font = `${fontSize * dpr}pt ${fontFamily}`;
  }
  function releaseContext () {
    if (textCanvasContext) {
      textCanvasContext.restore();
    }
  }
  return {getLabelSize, releaseContext};
}

export {
  DEFAULT_FONT_FAMILY,
  DEFAULT_FONT_SIZE_PT,
  getLabelSizer
};
