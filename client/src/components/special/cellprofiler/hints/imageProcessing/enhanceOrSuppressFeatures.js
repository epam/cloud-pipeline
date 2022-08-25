export default {
  'EnhanceOrSuppressFeatures': `
__EnhanceOrSuppressFeatures__ enhances or suppresses certain image features (such as speckles, ring shapes, and neurites), which can improve subsequent identification of objects.

This module enhances or suppresses the intensity of certain pixels relative to the rest of the image, by applying image processing filters to the image. It produces a grayscale image in which objects can be identified using an __Identify__ module.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES`
};
