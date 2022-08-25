export default {
  'ImageMath': `
__ImageMath__ performs simple mathematical operations on image intensities.

This module can perform addition, subtraction, multiplication, division, or averaging of two or more image intensities, as well as inversion, log transform, or scaling by a constant for individual image intensities.

Keep in mind that after the requested operations are carried out, the final image may have a substantially different range of pixel intensities than the original. CellProfiler assumes that the image is scaled from 0 – 1 for object identification and display purposes, so additional rescaling may be needed.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES`
};
