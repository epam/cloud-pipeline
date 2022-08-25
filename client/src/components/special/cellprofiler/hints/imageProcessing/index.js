import align from './align';
import colorToGray from './colorToGray';
import correctIlluminationApply from './correctIlluminationApply';
import correctIlluminationCalculate from './correctIlluminationCalculate';
import crop from './crop';
import enhanceEdges from './enhanceEdges';
import enhanceOrSuppressFeatures from './enhanceOrSuppressFeatures';
import flipAndRotate from './flipAndRotate';
import grayToColor from './grayToColor';
import imageMath from './imageMath';
import invertForPrinting from './invertForPrinting';
import makeProjection from './makeProjection';
import maskImage from './maskImage';
import morph from './morph';
import overlayOutlines from './overlayOutlines';
import rescaleIntensity from './rescaleIntensity';
import resize from './resize';
import smooth from './smooth';
import threshold from './threshold';
import tile from './tile';
import unmixColors from './unmixColors';

export default {
  ...align,
  ...colorToGray,
  ...correctIlluminationApply,
  ...correctIlluminationCalculate,
  ...crop,
  ...enhanceEdges,
  ...enhanceOrSuppressFeatures,
  ...flipAndRotate,
  ...grayToColor,
  ...imageMath,
  ...invertForPrinting,
  ...makeProjection,
  ...maskImage,
  ...morph,
  ...overlayOutlines,
  ...rescaleIntensity,
  ...resize,
  ...smooth,
  ...threshold,
  ...tile,
  ...unmixColors
};
