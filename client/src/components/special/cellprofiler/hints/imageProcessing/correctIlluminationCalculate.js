export default {
  'CorrectIlluminationCalculate': `
__CorrectIlluminationCalculate__ calculates an illumination function that is used to correct uneven illumination/lighting/shading or to reduce uneven background in images.

This module calculates an illumination function that can either be saved to the hard drive for later use or immediately applied to images later in the pipeline. This function will correct for the uneven illumination in images. Use the __CorrectIlluminationApply__ module to apply the function to the image to be corrected. Use __SaveImages__ to export an illumination function to the hard drive using the “npy” file format.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES`
};
