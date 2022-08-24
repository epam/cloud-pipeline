export default {
  'InvertForPrinting': `
__InvertForPrinting__ inverts fluorescent images into brightfield-looking images for printing.

This module turns a single or multi-channel immunofluorescent-stained image into an image that resembles a brightfield image stained with similarly colored stains, which generally prints better. You can operate on up to three grayscale images (representing the red, green, and blue channels of a color image) or on an image that is already a color image. The module can produce either three grayscale images or one color image as output. If you want to invert the grayscale intensities of an image, use __ImageMath__.

Supports 2D? YES

Supports 3D? NO

Respects masks? NO`
};
