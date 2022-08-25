export default {
  'EnhanceEdges': `
__EnhanceEdges__ enhances or identifies edges in an image, which can improve object identification or other downstream image processing.

This module enhances the edges (gradients - places where pixel intensities change dramatically) in a grayscale image. All methods other than Canny produce a grayscale image that can be used in an __Identify__ module or thresholded using the __Threshold__ module to produce a binary (black/white) mask of edges. The Canny algorithm produces a binary (black/white) mask image consisting of the edge pixels.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES`
};
