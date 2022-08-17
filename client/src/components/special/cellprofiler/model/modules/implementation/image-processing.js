/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/* eslint-disable max-len */

import React from 'react';
import {AnalysisTypes} from '../../common/analysis-types';
import OutlineConfig from '../../parameters/outline-objects-configuration';
import ImageMathImages, {
  SINGLE_FILE_OPERATIONS,
  VALUE_SUPPORTED_OPERATIONS
} from '../../parameters/image-selectors/image-math';
import GrayToColor, {VALUE_SUPPORTED_SCHEMES} from '../../parameters/image-selectors/gray-to-color';

const align = {
  name: 'Align',
  group: 'Image Processing',
  outputs: ['output1|file', 'output2|file'],
  parameters: [
    'Select the alignment method|[Mutual Information,Normalized Cross Correlation]|Mutual Information',
    'Crop mode|[Crop to aligned region,Pad images,Keep size]|Crop to aligned region',
    'Select the first input image|file',
    'Name the first output image|ALIAS output1',
    'Select the second input image|file',
    'Name the second output image|ALIAS output2'
    // 'Select the additional image|file',
    // 'Name the output image|ALIAS output',
    // 'Select how the alignment is to be applied|[Similarly,Separately]|Similarly'
  ]
};

const colorToGray = {
  name: 'ColorToGray',
  group: 'Image Processing',
  outputs: [
    'output|file|IF method==Combine',
    'outputRed|file|IF (convertred==true AND method==Split AND type==RGB)',
    'outputGreen|file|IF (convertgreen==true AND method==Split AND type==RGB)',
    'outputBlur|file|IF (convertblue==true AND method==Split AND type==RGB)',
    'outputHue|file|IF (converthue==true AND method==Split AND type==HSV)',
    'outputSaturation|file|IF (convertsaturation==true AND method==Split AND type==HSV)',
    'outputValue|file|IF (convertvalue==true AND method==Split AND type==HSV)'
  ],
  parameters: [
    'Select the input image|file',
    'Conversion method|[Combine,Split]|Combine|ALIAS=method',
    'Image type|[RGB,HSV,Channels]|RGB|ALIAS=type',
    'Name the output image|string|IF method==Combine|ALIAS output',

    'Convert red to gray?|boolean|true|IF (method==Split AND type==RGB)|ALIAS convertred',
    'Name the output image (red)|IF (convertred==true AND method==Split AND type==RGB)|ALIAS outputRed',
    'Convert green to gray?|boolean|true|IF (method==Split AND type==RGB)|ALIAS=convertgreen',
    'Name the output image (green)|IF (convertgreen==true AND method==Split AND type==RGB)|ALIAS outputGreen',
    'Convert blue to gray?|boolean|true|IF (method==Split AND type==RGB)|ALIAS=convertblue',
    'Name the output image (blue)|IF (convertblue==true AND method==Split AND type==RGB)|ALIAS outputBlue',

    'Convert hue to gray?|boolean|true|IF (method==Split AND type==HSV)|ALIAS=converthue',
    'Name the output image (hue)|IF (converthue==true AND method==Split AND type==HSV)|ALIAS outputHue',
    'Convert saturation to gray?|boolean|true|IF (method==Split AND type==HSV)|ALIAS=convertsaturation',
    'Name the output image (saturation)|IF (convertsaturation==true AND method==Split AND type==HSV)|ALIAS outputSaturation',
    'Convert value to gray?|boolean|true|IF (method==Split AND type==HSV)|ALIAS=convertvalue',
    'Name the output image (value)|IF (convertvalue==true AND method==Split AND type==HSV)|ALIAS outputValue',

    'Relative weight of the red channel|float|1.0|IF (type!=Channels AND method==Combine)',
    'Relative weight of the green channel|float|1.0|IF (type!=Channels AND method==Combine)',
    'Relative weight of the blue channel|float|1.0|IF (type!=Channels AND method==Combine)'

    // todo:
    // 'Channel number|integer|1',
    // 'Relative weight of the channel|float|1.0|IF method!=Split',
    // 'Image name|IF method==Split',
    // 'Channel count|integer|1'
  ]
};

const correctIlluminationApply = {
  name: 'CorrectIlluminationApply',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS output',
    'Select the illumination function|file',
    'Select how the illumination function is applied|[Divide,Subtract]|Divide',
    'Set output image values less than 0 equal to 0?|boolean|true',
    'Set output image values greater than 1 equal to 1?|boolean|true'
  ]
};

const correctIlluminationCalculate = {
  name: 'CorrectIlluminationCalculate',
  group: 'Image Processing',
  outputs: [
    'output|file',
    'averaged|file|IF img==true',
    'dilated|file|IF dilatedimg==true'
  ],
  parameters: [
    'Select the input image|file',
    'Name the output image|ALIAS output',
    'Select how the illumination function is calculated|[Regular,Background]|Regular|ALIAS=function',
    'Block size|integer|60|IF function==Background',
    'Dilate objects in the final averaged image?|boolean|false|IF function!=Background',
    'Rescale the illumination function?|[Yes,No,Median]|Yes',
    'Calculate function for each image individually, or based on all images?|[Each,All: First cycle,All: Across cycles]|Each',
    'Smoothing method|[No smoothing,Convex Hull,Fit Polynomal,Median Filter,Gaussian Filter,Smooth to Average,Splines]|No smoothing|ALIAS=method',
    'Method to calculate smoothing filter size|[Automatic,Object size,Manually]|Automatic|IF (method !=Median Filter AND method !=Gaussian Filter)|ALIAS=filtersize',
    'Automatically calculate spline parameters?|boolean|true|IF method=Splines|ALIAS=autosplines',
    'Approximate object diameter|integer|10|IF filtersize=Object size',
    'Smoothing filter size|integer|10|IF filtersize=Manually',
    'Background mode|[auto,dark,bright,gray]|auto|IF autosplines==false',
    'Number of spline points|integer|5|IF autosplines==false',
    'Background threshold|float]|2.0|IF autosplines==false',
    'Image resampling factor|float]|2.0|IF autosplines==false',
    'Maximum number of iterations|integer|40|IF autosplines==false',
    'Residual value for convergence|float]|0.001|IF autosplines==false',
    'Retain the averaged image?|boolean|false|ALIAS=img',
    'Name the averaged image|ALIAS averaged|IF img==true',
    'Retain the dilated image?|boolean|false|ALIAS=dilatedimg',
    'Name the dilated image|ALIAS dilated|IF dilatedimg==true'
  ]
};

// todo: parameters with ???
const crop = {
  name: 'Crop',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|Crop|ALIAS output',
    'Select the cropping shape|[Rectangle,Ellipse,Image,Objects,Previous cropping]|Rectangle|ALIAS=shape',
    'Select the masking image|file|IF shape==Image',
    'Select the objects|object|IF shape==Objects',
    'Select the image with a cropping mask|file|IF shape==Previous cropping',
    'Select the cropping method|[Coordinates,Mouse]|Coordinates|IF (shape==Rectangle OR shape==Ellipse)|ALIAS=method',
    'Apply which cycle\'s cropping pattern?|[Every,First]|Every|IF (shape==Rectangle OR shape==Ellipse)',
    // 'Left and right rectangle positions|???|0,end,Absolute|IF (shape==Rectangle AND method==Coordinates)',
    // 'Top and bottom rectangle positions|???|0,end,Absolute|IF (shape==Rectangle AND method==Coordinates)',
    // 'Coordinates of ellipse center|string???|500,500|IF (shape==Ellipse AND method==Coordinates)',
    'Ellipse radius, X direction|integer|400|IF (shape==Ellipse AND method==Coordinates)',
    'Ellipse radius, Y direction|integer|200|IF (shape==Ellipse AND method==Coordinates)',
    'Remove empty rows and columns?|[No,Edges,All]|All'
  ]
};

const enhanceEdges = {
  name: 'EnhanceEdges',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|EnhanceEdges|ALIAS output',
    'Select an edge-finding method|[Sobel,Prewitt,Roberts,LoG,Canny,Kirsch]|Sobel|ALIAS=method',
    'Calculate Gaussian\'s sigma automatically?|boolean|true|IF (method==LoG OR method==Canny)|ALIAS=autosigma',
    'Gaussian\'s sigma value|float|10.0|IF autosigma==false',
    'Automatically calculate the threshold?|boolean|true|ALIAS=autothreshold|IF method==Canny',
    'Absolute threshold|float|0.2|IF autothreshold==false',
    'Calculate value for low threshold automatically?|boolean|true|ALIAS=lowthreshold|IF method==Canny',
    'Threshold adjustment factor|float|1.0|IF method==Canny',
    'Low threshold value|float|0.1|IF (lowthreshold==false AND method==Canny)',
    'Select edge direction to enhance|[All,Horizontal,Vertical]|All|IF (method=Sobel OR method==Prewitt)'
  ]
};

const enhanceOrSuppressFeatures = {
  name: 'EnhanceOrSuppressFeatures',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|EnhanceOrSuppressFeatures|ALIAS output',
    'Select the operation|[Enhance,Suppress]|Enhance|ALIAS=operation',
    'Feature type|[Speckles,Neurites,Dark holes,Circles,Texture,DIC]|Speckles|IF operation==Enhance|ALIAS=feature',
    'Enhancement method|[Tubeness,Line structures]|Tubeness|IF (feature==Neurites AND operation==Enhance)|ALIAS=method',
    'Smoothing scale|float|2.0|IF ((method==Tubeness OR feature==Texture OR feature==DIC) AND operation==Enhance)|ALIAS smoothingScale',
    'Feature size|units|2|IF (operation==Suppress OR method=="Line structures" OR feature==Circles OR feature==Speckles)|ALIAS featureSize',
    'Range of hole sizes|units[0,Infinity]|[1,10]|IF (feature=="Dark holes" AND operation==Enhance)',
    'Shear angle|float|0.0|IF (feature==DIC AND operation==Enhance)',
    'Decay|float|0.95|IF (feature==DIC AND operation==Enhance)',
    'Speed and accuracy|[Fast,Slow]|Fast|IF (feature==Speckles AND operation==Enhance)',
    'Rescale result image|boolean|false|IF (feature==Neurites AND operation==Enhance)|ALIAS rescaleResult'
  ]
};

const flipAndRotate = {
  name: 'FlipAndRotate',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|string|FlipAndRotate|ALIAS output',
    'Select method to flip image|[Do not flip,Left to right,Top to bottom,Left to right and top to bottom]|Do not flip',
    'Select method to rotate image|[Do not rotate,Enter angle,Enter coordinates,Use mouse]|Do not rotate|ALIAS=rotatemethod',
    'Crop away the rotated edges?|boolean|true',
    'Enter angle of rotation|float|0.0|IF rotatemethod==Enter angle',
    'Enter coordinates of the top or left pixel. X|integer|0|IF rotatemethod==Enter coordinates|ALIAS x1',
    'Enter coordinates of the top or left pixel. Y|integer|0|IF rotatemethod==Enter coordinates|ALIAS y1',
    'Enter the coordinates of the bottom or right pixel. X|integer|0|IF rotatemethod==Enter coordinates|ALIAS x2',
    'Enter the coordinates of the bottom or right pixel. Y|integer|100|IF rotatemethod==Enter coordinates|ALIAS y2',
    'Enter coordinates of the top or left pixel|string|{x1},{y1}|HIDDEN|COMPUTED',
    'Enter the coordinates of the bottom or right pixel|string|0,100|{x2},{y2}|HIDDEN|COMPUTED',
    'Select how the specified points should be aligned|[horizontally,vertically]|horizontally|IF rotatemethod==Enter coordinates',
    'Calculate rotation|[Individually,Only Once]|Individually|IF rotatemethod==Use mouse'
  ]
};

const grayToColor = {
  name: 'GrayToColor',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Name the output image|string|GrayToColor|ALIAS output',
    'Select a color scheme|[RGB,CMYK,Stack,Composite]|RGB|ALIAS=scheme',
    'Select the image to be colored red|file|IF scheme==RGB|ALIAS=red',
    'Select the image to be colored green|file|IF scheme==RGB|ALIAS=green',
    'Select the image to be colored blue|file|IF scheme==RGB|ALIAS=blue',
    'Relative weight for the red image|float|1.0|IF red!="" AND scheme==RGB',
    'Relative weight for the green image|float|1.0|IF green!="" AND scheme==RGB',
    'Relative weight for the blue image|float|1.0|IF blue!="" AND scheme==RGB',
    'Rescale intensity|boolean|true|IF scheme!=Stack',
    'Select the image to be colored cyan|file|IF scheme==CMYK|ALIAS=cyan',
    'Select the image to be colored magenta|file|IF scheme==CMYK|ALIAS=magenta',
    'Select the image to be colored yellow|file|IF scheme==CMYK|ALIAS=yellow',
    'Select the image that determines brightness|file|IF scheme==CMYK|ALIAS=brightness',
    'Relative weight for the cyan image|float|1.0|IF cyan!="" AND scheme==CMYK',
    'Relative weight for the magenta image|float|1.0|IF magenta!="" AND scheme==CMYK',
    'Relative weight for the yellow image|float|1.0|IF yellow!="" AND scheme==CMYK',
    'Relative weight for the brightness image|float|1.0|IF brightness!="" AND scheme==CMYK',
    {
      name: 'inputs',
      title: 'Files',
      parameterName: 'Image name',
      type: AnalysisTypes.custom,
      valueParser: value => value,
      valueFormatter: (value, cpModule) => {
        const scheme = cpModule ? cpModule.getParameterValue('scheme') : 'Composite';
        const colorAndWeight = VALUE_SUPPORTED_SCHEMES.includes(scheme);
        return (value || []).map(({name, color, value}) => colorAndWeight ? `${name}|${value}|${color}` : name);
      },
      renderer: (moduleParameterValue, className, style) => (
        <GrayToColor
          parameterValue={moduleParameterValue}
          className={className}
          style={style}
        />
      ),
      visibilityHandler: (cpModule) => cpModule && ['Composite', 'Stack'].includes(cpModule.getParameterValue('scheme'))
    }
  ],
  notes: 'Add another channel button allows to add the last three parameters'
};

// todo: Other operations
// [Add,Subtract,Absolute Difference,Multiply,Divide,Average,Minimum,Maximum,Standard Deviation,Invert,Log transform (base 2),Log transform (legacy),And,Or,Not,Equals,None]
const imageMath = {
  name: 'ImageMath',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Operation|[Add,Subtract,Absolute Difference,Multiply,Divide,Average,Minimum,Maximum,Standard Deviation,Invert,"Log transform (base 2)","Log transform (legacy)",And,Or,Not,Equals,None]|Add|ALIAS=operation',
    'Name the output image|string|ImageMath|ALIAS output',
    {
      name: 'inputs',
      title: 'Files',
      parameterName: 'images',
      type: AnalysisTypes.custom,
      valueParser: value => value,
      valueFormatter: (value, cpModule) => {
        const operation = cpModule ? cpModule.getParameterValue('operation') : 'None';
        const multiplySupported = VALUE_SUPPORTED_OPERATIONS.includes(operation);
        const images = (value || [])
          .filter(({name}) => !!name)
          .map(({name, value}) => ({
            type: 'Image',
            value: name,
            factor: multiplySupported ? value : 1
          }));
        if (SINGLE_FILE_OPERATIONS.includes(operation)) {
          return images.slice(0, 1);
        }
        return images;
      },
      renderer: (moduleParameterValue, className, style) => (
        <ImageMathImages
          parameterValue={moduleParameterValue}
          className={className}
          style={style}
        />
      )
    },
    'Raise the power of the result by|float|1.0|IF (operation!=Not OR operation!=Equals)',
    'Multiply the result by|float|1.0|IF (operation!=Not OR operation!=Equals)|ALIAS multiply',
    'Add to result|float|0.0|IF (operation!=Not OR operation!=Equals)',
    'Set values less than 0 equal to 0?|boolean|true|IF (operation!=Not OR operation!=Equals)',
    'Set values greater than 1 equal to 1?|boolean|true|IF (operation!=Not OR operation!=Equals)',
    'Replace invalid values with 0?|boolean|true|IF (operation!=Not OR operation!=Equals)',
    'Ignore the image masks?|boolean|false'
  ]
};

const multiplyImageIntensity = {
  name: 'MultiplyImageIntensity',
  group: 'Image Processing',
  output: 'output|file',
  composed: true,
  parameters: [
    'Input image|file|ALIAS input',
    'Output image|string|ALIAS output',
    'Multiply intensity by|float|1|ALIAS ratio'
  ],
  subModules: [
    {
      module: 'ImageMath',
      values: {
        output: '{parent.output}|COMPUTED',
        operation: 'Add',
        multiply: '{parent.ratio}|COMPUTED',
        images: (cpModule, modules) => {
          const parent = modules.parent;
          if (!parent) {
            return undefined;
          }
          const input = parent.getParameterValue('input');
          if (input) {
            return [{
              name: input,
              value: 1
            }];
          }
          return [];
        }
      }
    }
  ]
};

const invertForPrinting = {
  name: 'InvertForPrinting',
  group: 'Image Processing',
  outputs: [
    'redImage|file|IF producered==true AND output==Grayscale',
    'greenImage|file|IF producegreen==true AND output==Grayscale',
    'blueImage|file|IF produceblue==true AND output==Grayscale',
    'invertedImage|file|IF output==Color'
  ],
  parameters: [
    'Input image type|[Color,Grayscale]|Color|ALIAS=type',
    'Use a red image?|boolean|true|IF type==Grayscale|ALIAS=usered',
    'Select the red image|file|IF (type==Grayscale AND usered==true)',
    'Use a green image?|boolean|true|IF type==Grayscale|ALIAS=usegreen',
    'Select the green image|file|IF type==Grayscale AND usegreen==true',
    'Use a blue image?|boolean|true|IF type==Grayscale|ALIAS=useblue',
    'Select the blue image|file|IF type==Grayscale AND useblue==true',
    'Select the color image|file|IF type==Color',
    'Output image type|[Color,Grayscale]|Color|ALIAS=output',
    'Select "*Yes*" to produce a red image.|boolean|true|ALIAS=producered|IF output==Grayscale',
    'Name the red image|string|RedImage|ALIAS redImage|IF producered==true AND output==Grayscale',
    'Select "*Yes*" to produce a green image.|boolean|true|ALIAS=producegreen|IF output==Grayscale',
    'Name the green image|string|GreenImage|ALIAS greenImage|IF producegreen==true AND output==Grayscale',
    'Select "*Yes*" to produce a blue image.|boolean|true|ALIAS=produceblue|IF output==Grayscale',
    'Name the blue image|string|BlueImage|ALIAS blueImage|IF produceblue==true AND output==Grayscale',
    'Name the inverted color image|string|InvertedColor|ALIAS invertedImage|IF output==Color'
  ]
};

const makeProjection = {
  name: 'MakeProjection',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Type of projection|[Average,Maximum,Minimum,Sum,Variance,Power,Brightfield,Mask]|Average|ALIAS=type',
    'Name the output image|ALIAS output',
    'Frequency|float|6.0|IF type==Power'
  ]
};

const maskImage = {
  name: 'MaskImage',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|MaskImage|ALIAS output',
    'Use objects or an image as a mask?|[Objects,Image]|Objects|ALIAS=mask',
    'Select object for mask|object|IF mask==Objects',
    'Select image for mask|file|IF mask==Image',
    'Invert the mask?|boolean|false'
  ]
};

const morph = {
  name: 'Morph',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|Morph|ALIAS output',
    'Select the operation to perform|[branchpoints,bridge,clean,convex hull,diag,distance,endpoints,fill,hbreak,majority,openlines,remove.shrink,skelpe,spur,thicken,thin,vbreak]|branchpoints|ALIAS=operation',
    'Number of times to repeat operation|[Once,Forever,Custom]|Once|IF (operation!=distance AND operation!=openlines)|ALIAS numberOfTimes',
    'Repetition number|integer|2|IF numberOfTimes==Custom AND operation!=distance AND operation!=openlines',
    'Rescale values from 0 to 1?|boolean|true|IF operation==distance',
    'Line length|integer|2|IF operation==openlines'
  ]
};

const overlayObjects = {
  name: 'OverlayObjects',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Input|file|ALIAS input',
    'Name the output image|string|OverlayObjects|ALIAS output',
    'Objects|object|ALIAS objects',
    'Opacity|float|0.3|ALIAS opacity'
  ]
};

const overlayOutlines = {
  name: 'OverlayOutlines',
  group: 'Image Processing',
  output: 'outputFile|file',
  parameters: [
    'Display outlines on a blank image?|boolean|false|ALIAS outlines',
    'Select image on which to display outlines|file|IF outlines==false',
    'Name the output image|ALIAS outputFile',
    'Outline display mode|[Color,Grayscale]|Color|ALIAS mode',
    'How to outline|[Inner,Outer,Thick]|Inner',
    'Select method to determine brightness of outlines|[Max of image,Max possible]|Max of image|IF mode==Grayscale|ALIAS method',
    {
      name: 'output',
      title: 'Objects to display',
      type: AnalysisTypes.custom,
      valueParser: value => {
        if (Array.isArray(value)) {
          return value.map(part => {
            if (typeof part === 'string') {
              const [name, color] = part.split('|');
              return {
                name,
                color
              };
            }
            if (typeof part === 'object' && part.name) {
              return part;
            }
            return undefined;
          }).filter(Boolean);
        }
        return value;
      },
      valueFormatter: (value, cpModule) => {
        const grayScale = cpModule &&
          cpModule.getParameterValue('mode') === 'Grayscale';
        return (value || []).map(({name, color}) => grayScale ? name : `${name}|${color}`);
      },
      renderer: (moduleParameterValue, className, style) => (
        <OutlineConfig
          parameterValue={moduleParameterValue}
          className={className}
          style={style}
        />
      )
    }
  ]
};

const rescaleIntensity = {
  name: 'RescaleIntensity',
  group: 'Image Processing',
  output: 'output|file',
  // todo: Divisor measurement parameter
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|RescaleIntensity|ALIAS output',
    `Rescaling method|[Stretch each image to use the full intensity range,Choose specific values to be reset to the full intensity range,Choose specific values to be reset to the full custom range,Divide by the image's minimum,Divide by the image's maximum,Divide each image by the same value,Divide each image by a previously calculated value,Match the image's maximum to another image's maximum]|Stretch each image to use the full intensity range|ALIAS=method`,
    'Method to calculate the minimum intensity|[Custom,Minimum for each image,Minimum of all images]|Custom|IF (method=="Choose specific values to be reset to the full intensity range" OR method=="Choose specific values to be reset to the full custom range")|ALIAS min',
    'Method to calculate the maximum intensity|[Custom,Maximum for each image,Maximum of all images]|Custom|IF (method=="Choose specific values to be reset to the full intensity range" OR method=="Choose specific values to be reset to the full custom range")|ALIAS max',
    'Upper intensity limit for the input image|float|1.0|IF (min!=Custom AND max==Custom)',
    'Lower intensity limit for the input image|float|0.0|IF (min==Custom AND max!=Custom)',
    'Intensity range for the input image|float[0.0,1.0]|[0.0,1.0]|IF (method=="Choose specific values to be reset to the full intensity range" OR method=="Choose specific values to be reset to the full custom range")',
    'Intensity range for the output image|float[0.0,1.0]|[0.0,1.0]|IF method=="Choose specific values to be reset to the full custom range"',
    'Divisor value|float|1.0|IF method=="Divide each image by the same value"',
    // 'Divisor measurement|объединение параметров Category и Measurement|IF method==Divide each image by a previously calculated value',
    // 'Category|[Count,FileName,Frame,Height,MD5Digest,PathName,Rotation,Scaling,Series,Threshold,URL,Width]|IF method=="Divide each image by a previously calculated value"|LOCAL|ALIAS category',
    // 'Measurement|[FinalThreshold,GuideThreshold,OrigThreshold,SumOfEntropies,WeightedVariance]|IF method=="Divide each image by a previously calculated value"|LOCAL|ALIAS measurement',
    `Select image to match in maximum intensity|file|IF method=="Match the image's maximum to another image's maximum"`
  ]
};

const resize = {
  name: 'Resize',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|Resized|ALIAS output',
    'Resizing method|[Resize by a fraction or multiple of the original size,Resize by specifying desired final dimensions]|Resize by a fraction or multiple of the original size|ALIAS method',
    'Resizing factor|float|0.25|ALIAS factor|IF method=="Resize by a fraction or multiple of the original size"',
    'Method to specify the dimensions|[Manual,Image]|Manual|IF method=="Resize by specifying desired final dimensions"|ALIAS specify',
    'Select the image with the desired dimensions|file|IF method=="Resize by specifying desired final dimensions" AND specify==Image',
    'Width of the final image|string|100|IF method=="Resize by specifying desired final dimensions" AND specify==Manual',
    'Height of the final image|string|100|IF method=="Resize by specifying desired final dimensions" AND specify==Manual',
    'Interpolation method|[Nearest Neighbor,Bilinear,Bicubic]|Nearest Neighbor',
    'Additional image count|string|0|HIDDEN'
  ]
};

const smooth = {
  name: 'Smooth',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|Smooth|ALIAS output',
    'Select smoothing method|[Fit Polynomial,Gaussian Filter,Median Filter,Smooth Keeping Edges,Circular Average Filter,Smooth to Average]|Fit Polynomial|ALIAS method',
    'Clip intensities to 0 and 1?|boolean|true|If method=="Fit Polynomial"',
    'Edge intensity difference|float|0.1|IF method=="Smooth Keeping Edges"',
    'Calculate artifact diameter automatically?|boolean|true|IF (method!="Fit Polynomial" AND method!="Smooth to Average")|ALIAS autoartifact',
    'Typical artifact diameter:|float|6.0|IF autoartifact!=true'
  ]
};

const threshold = {
  name: 'Threshold',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file|ALIAS input',
    'Name the output image|string|Threshold|ALIAS output',
    // Thresholding
    'Threshold strategy|[Global,Adaptive]|Global|ALIAS strategy',
    `Thresholding method|[Minimum Cross-Entropy,Otsu,Robust Background,Savuola|IF strategy==Adaptive,Measurement|IF strategy!=Adaptive,Manual|IF strategy!=Adaptive]|Minimum Cross-Entropy|ALIAS thresholdingMethod`,

    // Thresholding > Otsu
    'Two-class or three-class thresholding?|[Two classes, Three classes]|Two classes|IF thresholdingMethod==Otsu|ALIAS otsuMethodType',
    'Assign pixels in the middle intensity class to the foreground or the background?|[Background,Foreground]|Background|IF (thresholdingMethod==Otsu AND otsuMethodType=="Three classes")|ALIAS otsuThreePixels',

    // Thresholding > Robust Background
    'Lower outlier fraction|float|0.05|IF thresholdingMethod=="Robust Background"|ALIAS lowerOutlierFraction',
    'Upper outlier fraction|float|0.05|IF thresholdingMethod=="Robust Background"|ALIAS upperOutlierFraction',
    'Averaging method|[Mean,Median,Mode]|Mean|IF thresholdingMethod=="Robust Background"|ALIAS robustAveragingMethod',
    'Variance method|[Standard deviation,Median absolute deviation]|Standard deviation|IF thresholdingMethod=="Robust Background"|ALIAS varianceMethod',
    '# of deviations|integer|2|IF thresholdingMethod=="Robust Background"|ALIAS deviations',

    // Thresholding > Measurement
    {
      title: 'Select the measurement to threshold with',
      parameterName: 'Select the measurement to threshold with',
      isList: true,
      /**
       * @param {AnalysisModule} cpModule
       * @returns {{title: *, value: string}[]}
       */
      values: (cpModule) => {
        let inputName = 'input';
        if (cpModule) {
          const outputs = cpModule.channels || [];
          if (outputs.length) {
            inputName = outputs[0].name;
          }
        }
        return ['FileName', 'Frame', 'Height', 'MD5Digest', 'PathName', 'Scaling', 'Series', 'URL', 'Width']
          .map(method => ({title: method, value: `${method}_${inputName}`}));
      },
      visibilityHandler: (cpModule) =>
        cpModule.getParameterValue('thresholdingMethod') === 'Measurement'
    },

    // Thresholding > Manual
    'Manual threshold|float|0.0|IF thresholdingMethod==Manual AND strategy==Global|ALIAS manualThreshold',

    // Thresholding - common
    'Threshold smoothing scale|float|0.0|ALIAS thresholdSmoothingScale',
    'Threshold correction factor|float|1.0|IF thresholdingMethod!==Manual|ALIAS thresholdCorrectionFactor',
    'Lower and upper bounds on threshold|float[]|[0.0,1.0]|IF thresholdingMethod!==Manual|ALIAS bounds',
    'Size of adaptive window|integer|50|IF strategy==Adaptive|ALIAS adaptive',
    'Log transform before thresholding?|flag|false|IF thresholdingMethod==Otsu OR thresholdingMethod=="Minimum Cross-Entropy"|ALIAS logTransform'

  ]
};

const tile = {
  name: 'Tile',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input image|file',
    'Name the output image|string|Tile|ALIAS output',
    'Tile assembly method|[Within cycles,Across cycles]|Within cycles|ALIAS method',
    'Automatically calculate number of rows?|boolean|false|ALIAS autorows',
    'Final number of rows|integer|8|IF autorows!=true',
    'Automatically calculate number of columns?|boolean|false|ALIAS autocolumns',
    'Final number of columns|integer|8|IF autocolumns!=true',
    'Image corner to begin tiling|[top let,bottom left,top right,bottom right]|top left',
    'Direction to begin tiling|[row,column]|row',
    'Use meander mode?|boolean|false'
  ]
};

const unmixColors = {
  name: 'UnmixColors',
  group: 'Image Processing',
  output: 'output|file',
  parameters: [
    'Select the input color image|file',
    'Name the output image|string|UnmixColors|ALIAS output',
    'Stain|[AEC,Alican blue,Aniline blue,Azocarmine,DAB,Eosin,Fast blue,Fast red,Feulgen,Hematoxylin,Hematoxylin and PAS,Methyl blue,Methyl green,Methylene blue,Orange-G,PAS,Ponceau-fuchsin,Custom]|AEC|ALIAS stain',
    'Red absorbance|float|0.5|IF stain==Custom',
    'Green absorbance|float|0.5|IF stain==Custom',
    'Blue absorbance|float|0.5|IF stain==Custom'
  ]
};

export default [
  align,
  colorToGray,
  correctIlluminationApply,
  correctIlluminationCalculate,
  crop,
  enhanceEdges,
  enhanceOrSuppressFeatures,
  flipAndRotate,
  grayToColor,
  imageMath,
  invertForPrinting,
  maskImage,
  makeProjection,
  morph,
  multiplyImageIntensity,
  rescaleIntensity,
  resize,
  smooth,
  threshold,
  tile,
  overlayObjects,
  overlayOutlines,
  unmixColors
];
