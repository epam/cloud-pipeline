import closing from './closing';
import dilateImage from './dilateImage';
import dilateObjects from './dilateObjects';
import erodeImage from './erodeImage';
import erodeObjects from './erodeObjects';
import fillObjects from './fillObjects';
import gaussianFilter from './gaussianFilter';
import matchTemplate from './matchTemplate';
import medialAxis from './medialAxis';
import medianFilter from './medianFilter';
import morphologicalSkeleton from './morphologicalSkeleton';
import opening from './opening';
import reduceNoise from './reduceNoise';
import removeHoles from './removeHoles';
import shrinkToObjectCenters from './shrinkToObjectCenters';
import watershed from './watershed';

export default {
  ...closing,
  ...dilateImage,
  ...dilateObjects,
  ...erodeImage,
  ...erodeObjects,
  ...fillObjects,
  ...gaussianFilter,
  ...matchTemplate,
  ...medialAxis,
  ...medianFilter,
  ...morphologicalSkeleton,
  ...opening,
  ...reduceNoise,
  ...removeHoles,
  ...shrinkToObjectCenters,
  ...watershed
};
