export default {
  'UnmixColors': `
__UnmixColors__ creates separate images per dye stain for histologically stained images.

This module creates separate grayscale images from a color image stained with light-absorbing dyes. Dyes are assumed to absorb an amount of light in the red, green and blue channels that increases proportionally in each channel with increasing amounts of stain; the hue does not shift with increasing staining. The module separates two or more stains from a background, producing grayscale images. There are several pre-set dye combinations as well as a custom mode that allows you to calibrate using two images stained with a single dye each. Some commonly known stains must be specified by the individual dye components. For example:

* Azan-Mallory: Anilline Blue + Azocarmine + Orange-G
* Giemsa: Methylene Blue or Eosin
* Masson Trichrome: Methyl blue + Ponceau-Fuchsin

If there are non-stained cells/components that you also want to separate by color, choose the stain that most closely resembles the color you want, or enter a custom value. Please note that if you are looking to simply split a color image into red, green and blue components, use the __ColorToGray__ module rather than __UnmixColors__.  

Supports 2D? YES

Supports 3D? NO

Respects masks? NO`
};
