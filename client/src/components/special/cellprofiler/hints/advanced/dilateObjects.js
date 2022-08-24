export default {
  'DilateObjects': `
__DilateObjects__ expands objects based on the structuring element provided. This function is similar to the “Expand” function of __ExpandOrShrinkObjects__, with two major distinctions-

1.__DilateObjects__ supports 3D objects, unlike __ExpandOrShrinkObjects__.

2.In __ExpandOrShrinkObjects__, two objects closer than the expansion distance will expand until they meet and then stop there. In this module, the object with the larger object number (the object that is lower in the image) will be expanded on top of the object with the smaller object number.

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
