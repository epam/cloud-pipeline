export default {
  'IdentifyPrimaryObjects': `
__IdentifyPrimaryObjects__ identifies biological objects of interest. It requires grayscale images containing bright objects on a dark background. Incoming images must be 2D (including 2D slices of 3D images); please use the __Watershed__ module for identification of objects in 3D.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

__Measurements made by this module__

Image measurements:
  * <em>Count</em>: the number of primary objects identified.
  * <em>OriginalThreshold</em>: the global threshold for the image.
  * <em>FinalThreshold</em>: for the global threshold methods, this value is the same as <em>OriginalThreshold</em>. For the adaptive or per-object methods, this value is the mean of the local thresholds.
  * <em>WeightedVariance</em>: the sum of the log-transformed variances of the foreground and background pixels, weighted by the number of pixels in each distribution.
  * <em>SumOfEntropies</em>: the sum of entropies computed from the foreground and background distributions.

Object measurements:
  * <em>Location_X</em>, <em>Location_Y</em>: the pixel (X,Y) coordinates of the primary object centroids. The centroid is calculated as the center of mass of the binary representation of the object.`,

  'IdentifyPrimaryObjects.input': `
To use this module, you will need to make sure that your input image has the following qualities:
  * The image should be grayscale.
  * The foreground (i.e, regions of interest) are lighter than the background.
  * The image should be 2D. 2D slices of 3D images are acceptable if the image has not been loaded as volumetric in the __NamesAndTypes__ module. For volumetric analysis of 3D images, please see the __Watershed__ module.

If this is not the case, other modules can be used to pre-process the images to ensure they are in the proper form:
  * If the objects in your images are dark on a light background, you should invert the images using the Invert operation in the __ImageMath__ module.
  * If you are working with color images, they must first be converted to grayscale using the __ColorToGray__ module.
  * If your images are brightfield/phase/DIC, they may be processed with the __EnhanceOrSuppressFeatures__ module with its <em>“Texture”</em> or <em>“DIC”</em> settings.`,

  'IdentifyPrimaryObjects.advanced': `
__IdentifyPrimaryObjects__ allows you to tweak your settings in many ways; so many that it can often become confusing where you should start. This is typically the most important but complex step in creating a good pipeline, so do not be discouraged: other modules are easier to configure! Using __IdentifyPrimaryObjects__ with <em>‘Use advanced settings?’</em> set to <em>‘No’</em> allows you to quickly try to identify your objects based only their typical size. If you are happy with the results produced by the default settings, you can then move on to construct the rest of your pipeline; if not, you can set <em>‘Use advanced settings?’</em> to <em>‘Yes’</em> which will allow you to fully tweak and customize all the settings.`
};
