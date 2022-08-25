export default {
  'MatchTemplate': `
The __MatchTemplate__ module uses normalized cross-correlation to match a template to a single-channel two-or-three dimensional image or multi-channel two-dimensional image. The output of the module is an image where each pixel corresponds to the Pearson product-moment correlation coefficient between the image and the template. Practically, this allows you to crop a single object of interest (i.e., a cell) and predict where other such objects are in the image. Note that this is not rotation invariant, so this module will perform best when objects are approximately round or are angled in a relatively unified direction.

Supports 2D? YES

Supports 3D? NO

Respects masks? NO`
};
