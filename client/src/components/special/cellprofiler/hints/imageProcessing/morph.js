export default {
  'Morph': `
__Morph__ performs low-level morphological operations on binary or grayscale images.

This module performs a series of morphological operations on a binary image or grayscale image, resulting in an image of the same type. Many require some image processing knowledge to understand how best to use these morphological filters in order to achieve the desired result. Note that the algorithms minimize the interference of masked pixels.

Supports 2D? YES

Supports 3D? NO

Respects masks? YES`,

  'Morph.operation': `
  <em>Branchpoints</em>: removes all pixels except those that are the branchpoints of a skeleton. This operation should be applied to an image after skeletonizing. It leaves only those pixels that are at the intersection of branches.

  <em>Bridge</em>: sets a pixel to 1 if it has two non-zero neighbors that are on opposite sides of this pixel.

  <em>Clean</em>: removes isolated pixels.

  <em>Convex hull</em>: finds the convex hull of a binary image. The convex hull is the smallest convex polygon that fits around all foreground pixels of the image: it is the shape that a rubber band would take if stretched around the foreground pixels. The convex hull can be used to regularize the boundary of a large, single object in an image, for instance, the edge of a well.

  <em>Diag</em>: fills in pixels whose neighbors are diagonally connected to 4-connect pixels that are 8-connected.

  <em>Distance</em>: computes the distance transform of a binary image. The distance of each foreground pixel is computed to the nearest background pixel. The resulting image is then scaled so that the largest distance is 1.

  <em>Endpoints</em>: removes all pixels except the ones that are at the end of a skeleton.

  <em>Fill</em>: sets a pixel to 1 if all of its neighbors are 1.

  <em>Hbreak</em>: removes pixels that form vertical bridges between horizontal lines.

  <em>Majority</em>: each pixel takes on the value of the majority that surround it (keep pixel value to break ties).

  <em>OpenLines</em>: performs an erosion followed by a dilation using rotating linear structural elements. The effect is to return parts of the image that have a linear intensity distribution and suppress dots of the same size.

  <em>Remove</em>: removes pixels that are otherwise surrounded by others (4 connected). The effect is to leave the perimeter of a solid object.

  <em>Shrink</em>: performs a thinning operation that erodes unless that operation would change the image’s Euler number. This means that blobs are reduced to single points and blobs with holes are reduced to rings if shrunken indefinitely.

  <em>SkelPE</em>: performs a skeletonizing operation using the metric, PE * D to control the erosion order. PE is the Poisson Equation evaluated within the foreground with the boundary condition that the background is zero. D is the distance transform (distance of a pixel to the nearest edge). The resulting skeleton has fewer spurs but some bit of erosion at the endpoints in the binary image.

  <em>Spur</em>: removes spur pixels, i.e., pixels that have exactly one 8-connected neighbor. This operation essentially removes the endpoints of lines.

  <em>Thicken</em>: dilates the exteriors of objects where that dilation does not 8-connect the object with another. The image is labeled and the labeled objects are filled. Unlabeled points adjacent to uniquely labeled points change from background to foreground.

  <em>Thin</em>: thin lines preserving the Euler number using the thinning algorithm # 1. The result generally preserves the lines in an image while eroding their thickness.

  <em>Vbreak</em>: removes pixels that form horizontal bridges between vertical lines.`
};
