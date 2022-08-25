export default {
  'ReduceNoise': `
__ReduceNoise__ performs non-local means noise reduction. Instead of only using a neighborhood of pixels around a central pixel for denoising, such as in __GaussianFilter__, multiple neighborhoods are pooled together. The neighborhood pool is determined by scanning the image for regions similar to the area around the central pixel using a correlation metric and a cutoff value.

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
