export default {
  'IdentifySecondaryObjects': `
__IdentifySecondaryObjects__ identifies objects (e.g., cells) using objects identified by another module (e.g., nuclei) as a starting point.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

For densely-packed cells (such as those in a confluent monolayer), determining the cell borders using a cell body stain can be quite difficult since they often have irregular intensity patterns and are lower-contrast with more diffuse staining. In addition, cells often touch their neighbors making it harder to delineate the cell borders. It is often easier to identify an organelle which is well separated spatially (such as the nucleus) as an object first and then use that object to guide the detection of the cell borders. See the __IdentifyPrimaryObjects__ module for details on how to identify a primary object.

In order to identify the edges of secondary objects, this module performs two tasks:
  1. Finds the dividing lines between secondary objects that touch each other.
  2. Finds the dividing lines between the secondary objects and the background of the image. In most cases, this is done by thresholding the image stained for the secondary objects.

__Measurements made by this module__

Image measurements:
  * <em>Count</em>: the number of secondary objects identified.
  * <em>OriginalThreshold</em>: the global threshold for the image.
  * <em>FinalThreshold</em>: for the global threshold methods, this value is the same as <em>OriginalThreshold</em>. For the adaptive or per-object methods, this value is the mean of the local thresholds.
  * <em>WeightedVariance</em>: the sum of the log-transformed variances of the foreground and background pixels, weighted by the number of pixels in each distribution.
  * <em>SumOfEntropies</em>: the sum of entropies computed from the foreground and background distributions.

Object measurements:
  * <em>Parent</em>: the identity of the primary object associated with each secondary object.
  * <em>Location_X</em>, <em>Location_Y</em>: the pixel (X,Y) coordinates of the center of mass of the identified secondary objects.`,

  'IdentifySecondaryObjects.input': `
An <em>image</em> highlighting the image features defining the edges of the secondary objects (e.g., cell edges). This is typically a fluorescent stain for the cell body, membrane or cytoskeleton (e.g., phalloidin staining for actin). However, any image that produces these features can be used for this purpose. For example, an image processing module might be used to transform a brightfield image into one that captures the characteristics of a cell body fluorescent stain. This input is optional because you can instead define secondary objects as a fixed distance around each primary object.`,

  'IdentifySecondaryObjects.inputObjects': `
An <em>object</em> (e.g., nuclei) identified from a prior module. These are typically produced by an __IdentifyPrimaryObjects__ module, but any object produced by another module may be selected for this purpose.`
};
