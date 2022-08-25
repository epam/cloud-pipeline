export default {
  'Crop': `
__Crop__ crops or masks an image.

This module crops images into a rectangle, ellipse, an arbitrary shape provided by you, the shape of object(s) identified by an __Identify__ module, or a shape created using a previous __Crop__ module in the pipeline.
  
Keep in mind that cropping changes the size of your images, which may have unexpected consequences. For example, identifying objects in a cropped image and then trying to measure their intensity in the <em>original</em> image will not work because the two images are not the same size.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES

__Measurements made by this module__

* <em>AreaRetainedAfterCropping</em>: The area of the image left after cropping.
* <em>OriginalImageArea</em>: The area of the original input image.

<em>Special note on saving images</em>: You can save the cropping shape that you have defined in this module (e.g., an ellipse you drew) so that you can use the Image option in future analyses. To do this, save either the mask or cropping in __SaveImages__.`
};
