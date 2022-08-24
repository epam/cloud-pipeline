import convertObjectsToImage from './convertObjectsToImage';
import filterObjects from './filterObjects';
import identifyPrimaryObjects from './identifyPrimaryObjects';
import identifySecondaryObjects from './identifySecondaryObjects';
import identifyTertiaryObjects from './identifyTertiaryObjects';
import maskObjects from './maskObjects';
import relateObjects from './relateObjects';
import resizeObjects from './resizeObjects';

export default {
  ...convertObjectsToImage,
  ...filterObjects,
  ...identifyPrimaryObjects,
  ...identifySecondaryObjects,
  ...identifyTertiaryObjects,
  ...maskObjects,
  ...relateObjects,
  ...resizeObjects
};
