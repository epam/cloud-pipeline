export default {
  'RemoveHoles': `
__RemoveHoles__ fills holes smaller than the specified diameter.

This module works best on binary and integer-labeled images (i.e., the output of __ConvertObjectsToImage__ when the color format is <em>uint16</em>). Grayscale and multichannel image data is converted to binary by setting values below 50% of the data range to 0 and the other 50% of values to 1.

The output of this module is a binary image, regardless of the input data type. It is recommended that __RemoveHoles__ is run before any labeling or segmentation module (e.g., __ConvertImageToObjects__ or __Watershed__).

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
