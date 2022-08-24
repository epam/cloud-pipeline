export default {
  'FillObjects': `
__FillObjects__ fills holes within all objects in an image.

__FillObjects__ can be run <em>after</em> any labeling or segmentation module (e.g., __ConvertImageToObjects__ or __Watershed__). Labels are preserved and, where possible, holes entirely within the boundary of labeled objects are filled with the surrounding object number.
  
__FillObjects__ can also be optionally run on a “per-plane” basis working with volumetric data. Holes will be filled for each XY plane, rather than on the whole volume.

Supports 2D? YES

Supports 3D? YES

Respects masks? NO`
};
