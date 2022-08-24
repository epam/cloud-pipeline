export default {
  'Watershed': `
__Watershed__ is a segmentation algorithm. It is used to separate different objects in an image.

Starting from user-defined markers, the watershed algorithm treats pixels values as a local topography (elevation). The algorithm floods basins from the markers until basins attributed to different markers meet on watershed lines. In many cases, markers are chosen as local minima of the image, from which basins are flooded

Supports 2D? YES

Supports 3D? YES

Respects masks? YES`,

  'Watershed.generate': `
__Distance__: This is classical nuclei segmentation using watershed. Your “Input” image should be a binary image. Markers and other inputs for the watershed algorithm will be automatically generated.

__Markers__: Similar to the IdentifySecondaryObjects in 2D, use manually generated markers and supply an optional mask for watershed. Watershed works best when the “Input” image has high intensity surrounding regions of interest and low intensity inside regions of interest.`,

  'Watershed.footprint': `
__Footprint__ defines dimentions of the window used to scan the input image for local maximum. The footprint can be interpreted as a region, window, structring element or volume that subsamples the input image. The distance transform will create local maximum from a binary image that will be at the centers of objects. A large footprint will suppress local maximum that are close together into a single maximum, but this will require more memory and time to run. Large footprint could result in a blockier segmentation. A small footprint will preserve local maximum that are close together, but this can lead to oversegmentation. If speed and memory are issues, choosing a lower footprint can be offset by downsampling the input image.`,

  'Watershed.downsample': `
__Downsample__ an n-dimensional image by local averaging. If the downsampling factor is 1, the image is not downsampled. To downsample more, increase the number from 1.`,

  'Watershed.connectivity': `
__Connectivity__ is the maximum number of orthogonal hops to consider a pixel/voxel as a neighbor. Accepted values are ranging from 1 to the number of dimensions. Two pixels are connected when they are neighbors and have the same value. In 2D, they can be neighbors either in a 1- or 2-connected sense. The value refers to the maximum number of orthogonal hops to consider a pixel/voxel a neighbor.

Note: when using marker-based __Watershed__ that it is typical to use the input binary image as the mask. Otherwise, if the mask is <em>None</em>, the background will be interpreted as an object and __Watershed__ may yield unexpected results.`,

  'Watershed.compactness': `
__Compactness__, use compact watershed with given compactness parameter. Higher values result in more regularly-shaped watershed basins.`
};
