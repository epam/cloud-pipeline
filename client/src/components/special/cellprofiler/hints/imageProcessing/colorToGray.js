export default {
  'ColorToGray': `
__ColorToGray__ converts an image with multiple color channels to one or more grayscale images.

This module converts color and channel-stacked images to grayscale. All channels can be merged into one grayscale image (<em>Combine</em>), or each channel can be extracted into a separate grayscale image (<em>Split</em>). If you use <em>Combine</em>, the relative weights you provide allow adjusting the contribution of the colors relative to each other. Note that all __Identify__ modules require grayscale images.

Supports 2D? YES

Supports 3D? NO

Respects masks? NO`
};
