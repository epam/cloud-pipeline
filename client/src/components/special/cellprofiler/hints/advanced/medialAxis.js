export default {
  'MedialAxis': `
__MedialAxis__ computes the medial axis or topological skeleton of a binary image. Rather than by sequentially removing pixels as in __MorphologicalSkeleton__, the medial axis is computed based on the distance transform of the thresholded image (i.e., the distance each foreground pixel is from a background pixel).

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
