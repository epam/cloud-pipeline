export default {
  'ErodeObjects': `
__ErodeObjects__ shrinks objects based on the structuring element provided. This function is similar to the “Shrink” function of __ExpandOrShrinkObjects__, with two major distinctions-

1.__ErodeObjects__ supports 3D objects, unlike __ExpandOrShrinkObjects__.

2.In __ExpandOrShrinkObjects__, a small object will only ever be shrunk down to a single pixel. In this module, an object smaller than the structuring element will be removed entirely unless ‘Prevent object removal’ is enabled.

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
