export default {
  'Align': `
__Align__ aligns images relative to each other, for example, to correct shifts in the optical path of a microscope in each channel of a multi-channel set of images.

For two or more input images, this module determines the optimal alignment among them. Aligning images is useful to obtain proper measurements of the intensities in one channel based on objects identified in another channel, for example. Alignment is often needed when the microscope is not perfectly calibrated. It can also be useful to align images in a time-lapse series of images. The module stores the amount of shift between images as a measurement, which can be useful for quality control purposes.
  
Note that the second image (and others following) is always aligned with respect to the first image. That is, the X/Y offsets indicate how much the second image needs to be shifted by to match the first.
  
This module does not perform warping or rotation, it simply shifts images in X and Y.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

__Measurements made by this module__

  * <em>Xshift, Yshift</em>: The pixel shift in X and Y of the aligned image with respect to the original image.`
};
