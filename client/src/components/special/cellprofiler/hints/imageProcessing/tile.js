export default {
  'Tile': `
__Tile__ tiles images together to form large montage images.

This module allows more than one image to be placed next to each other in a grid layout you specify. It might be helpful, for example, to place images adjacent to each other when multiple fields of view have been imaged for the same sample. Images can be tiled either across cycles (multiple fields of view, for example) or within a cycle (multiple channels of the same field of view, for example).  

Supports 2D? YES

Supports 3D? NO

Respects masks? NO

Tiling images to create a montage with this module generates an image that is roughly the size of all the images’ sizes added together. For large numbers of images, this may cause memory errors, which might be avoided by the following suggestions:

* Resize the images to a fraction of their original size, using the __Resize__ module prior to this module in the pipeline.
* Rescale the images to 8-bit using the __RescaleIntensity__ module, which diminishes image quality by decreasing the number of graylevels in the image (that is, bit depth) but also decreases the size of the image.

Please also note that this module does not perform image stitching (i.e., intelligent adjustment of the alignment between adjacent images).`
};
