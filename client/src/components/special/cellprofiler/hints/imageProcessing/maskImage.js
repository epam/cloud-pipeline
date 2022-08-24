export default {
  'MaskImage': `
__MaskImage__ hides certain portions of an image (based on previously identified objects or a binary image) so they are ignored by subsequent mask-respecting modules in the pipeline.

This module masks an image so you can use the mask downstream in the pipeline. The masked image is based on the original image and the masking object or image that is selected. If using a masking image, the mask is composed of the foreground (white portions); if using a masking object, the mask is composed of the area within the object. Note that the image created by this module for further processing downstream is grayscale. If a binary mask is desired in subsequent modules, use the __Threshold__ module instead of __MaskImage__.

Supports 2D? YES

Supports 3D? YES

Respects masks? YES`
};
