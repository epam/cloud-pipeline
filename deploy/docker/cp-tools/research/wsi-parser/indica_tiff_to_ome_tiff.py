import argparse
from xml.etree import ElementTree

from tifffile import TiffFile, TiffWriter


def tiles(series):
    # yield raw tiles from all pages in TIFF series
    fh = series.parent.filehandle
    for page in series:
        for offset, bytecount in zip(page.dataoffsets, page.databytecounts):
            fh.seek(offset)
            yield fh.read(bytecount)

def process_file(file_in, file_out):
    with TiffFile(file_in) as tif:
        print("Processing indica tif file: " + tif)

        tree = ElementTree.fromstring(tif.pages.first.description)
        channel_names = [channel.attrib['name'] for channel in tree.iter('channel')]

        with TiffWriter(file_out, bigtiff=True, ome=True, byteorder=tif.byteorder) as ome:
            for series in tif.series:
                print(series)
                assert series.axes == 'IYX'
                assert series.shape[0] == len(channel_names)

                for i, level in enumerate(series.levels):
                    page = level.keyframe
                    print(page)
                    assert page.is_tiled
                    if i == 0:
                        # base level
                        if len(series.levels) > 1:
                            subifds = len(series.levels) - 1
                        else:
                            subifds = None
                        resx, resy = page.get_resolution('micrometer')
                        metadata = {
                            'axes': 'CYX',
                            'PhysicalSizeX': resx,
                            'PhysicalSizeXUnit': 'µm',
                            'PhysicalSizeY': resy,
                            'PhysicalSizeYUnit': 'µm',
                            'Channel': {'Name': channel_names},
                        }
                    else:
                        subifds = None
                        metadata = None
                    dtype = 'float32' if level.dtype == 'uint32' else level.dtype
                    ome.write(
                        tiles(level),
                        shape=level.shape,
                        dtype=dtype,
                        photometric=page.photometric,
                        compression=page.compression,
                        resolution=page.resolution,
                        resolutionunit=page.resolutionunit,
                        tile=page.tile,
                        subifds=subifds,
                        metadata=metadata,
                    )

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)

    args = parser.parse_args()
    process_file(args.input, args.input)