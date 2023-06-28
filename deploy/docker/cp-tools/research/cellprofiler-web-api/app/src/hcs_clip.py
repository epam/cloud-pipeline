# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from .hcs_utils import get_required_field, prepare_cloud_path
from .config import Config
import os
from .modules.tifffile_with_offsets import TiffFile, TiffPage
import numpy as np
from moviepy.editor import ImageSequenceClip
import time
from PIL import Image, ImageOps
import xml.etree.ElementTree as ET
import json
import errno
import uuid


def create_clip(params):
    t = time.time()

    clip_format = params.get('format') if 'format' in params else '.webm'
    codec = params.get('codec') if 'codec' in params else ('mpeg4' if clip_format == '.mp4' else None)
    # fps for whole clip
    fps = int(params.get('fps')) if 'fps' in params else 1
    # frame duration
    duration = float(params.get('duration')) if 'duration' in params else 1
    if duration < 1:
        raise RuntimeError('Duration should be >= 1')
    sequence_id = params.get('sequenceId') if 'sequenceId' in params else None
    # by_time = 1 - create video for all timepoints and specified z-plane id
    # by_time = 0 - create video for all z-planes and specified time point id
    by_time = int(params.get('byTime')) if 'byTime' in params else 1
    # z-plane id if by_time = 1, time point id if by_time = 0
    point_id = params.get('pointId') if 'pointId' in params else '1'

    path = get_required_field(params, 'path')
    path = prepare_cloud_path(path)
    # by_field = 1 - create video for specified well field
    # by_field = 0 - create video for specified well
    by_field = int(get_required_field(params, 'byField'))
    # well or well field id
    cell = int(get_required_field(params, 'cell'))

    preview_dir, sequences = parse_hcs(path, sequence_id)
    preview_dir = prepare_cloud_path(preview_dir)
    index_path = os.path.join(preview_dir, 'Index.xml')
    if not os.path.isfile(index_path):
        index_path = os.path.join(preview_dir, 'index.xml')
    # hcs channels and z-planes
    channels, planes = get_planes_and_channels(index_path)
    if by_time:
        if point_id not in planes:
            raise RuntimeError('Incorrect Z plane id [{}]'.format(point_id))
    selected_channels = get_selected_channels(params, channels)

    clips = []
    durations = []
    for seq in sequences.keys():
        timepoints = sequences[seq]
        if not by_time:
            if point_id not in timepoints:
                raise RuntimeError('Incorrect timepoint id [{}]'.format(point_id))
        preview_seq_dir = os.path.join(preview_dir, '{}'.format(seq))
        ome_tiff_path, offsets_path = get_path(preview_seq_dir, by_field)
        pages = get_pages(list(selected_channels.keys()), point_id, planes, len(channels), by_time, timepoints, cell)
        set_offsets(offsets_path, pages)
        read_images(ome_tiff_path, pages)
        frames = timepoints if by_time else planes
        for f in frames:
            frame_pages = _get_items(pages.values(), 'time_point' if by_time else 'plane', f)
            frame_images = []
            for frame_page in frame_pages:
                channel_id = frame_page['channel_id']
                colored_image = color_image(frame_page['image'], selected_channels[channel_id])
                frame_images.append(colored_image)
            merged_image = merge_hcs_channels(frame_images)
            clips.append(np.array(merged_image))
            durations.append(duration)

    clip_name = get_clip_name(by_field, cell, clip_format, sequence_id, point_id, by_time)
    slide = ImageSequenceClip(clips, durations=durations)
    slide.write_videofile(clip_name, codec=codec, fps=fps)
    t1 = time.time()
    return clip_name, round((t1 - t), 2)


def read_images(ome_tiff_path, pages):
    tiff_file = TiffFile(ome_tiff_path)
    for page_num, page in pages.items():
        image = TiffPage(tiff_file, index=page_num, offset=page['offset']).asarray()
        page['image'] = image


def get_clip_name(by_field, cell, clip_format, sequence_id, point_id, by_time):
    clip_dir = os.path.join(Config.COMMON_RESULTS_DIR, 'video', str(uuid.uuid4()))
    mkdir(clip_dir)
    cell_prefix = ('field{}' if by_field else 'well{}').format(str(cell))
    prefix = ('plane{}' if by_time else 'tp{}').format(point_id)
    sequence_prefix = '' if sequence_id is None else 'seq{}'.format(sequence_id)
    clip_name = cell_prefix + sequence_prefix + prefix + clip_format
    return os.path.join(clip_dir, clip_name)


def build_image_path(image_extension, z_plane, channels, timepoint, sequence_id, fields):
    images_dir = os.path.join(Config.COMMON_RESULTS_DIR, 'images', str(uuid.uuid4()))
    mkdir(images_dir)
    field_prefix = ''
    for field_id in fields:
        field_prefix = field_prefix + 'f{}'.format(str(field_id))
    prefix = 'p{}t{}seq{}'.format(z_plane, timepoint, sequence_id)
    for channel_id in [int(channel) for channel in channels.keys()]:
        prefix = prefix + 'ch{}'.format(channel_id)
    if not str(image_extension).startswith('.'):
        image_extension = '.{}'.format(image_extension)
    image_name = field_prefix + prefix + image_extension
    return os.path.join(images_dir, image_name)


def get_pages(channel_ids, point_id, planes, channels, by_time, timepoints, cell):
    num = 0
    pages = dict()
    for time_point in timepoints:
        for channel_id in range(channels):
            for plane in planes:
                if (channel_id in channel_ids) and (plane == point_id if by_time else time_point == point_id):
                    page_num = num + len(planes) * channels * len(timepoints) * cell
                    pages[page_num] = {'channel_id': channel_id, 'time_point': time_point, 'plane': plane}
                num = num + 1
    return pages


def set_offsets(offsets_path, pages):
    with open(offsets_path, 'r') as offsets_file:
        offsets_line = offsets_file.read()
        offsets = offsets_line.replace("[", "").replace("]", "").split(", ")
        for page_num, page in pages.items():
            page['offset'] = int(offsets[page_num])


def get_pages_for_fields(channel_ids, z_plane, planes, channels_count, timepoint, timepoints, field_ids):
    pages = dict()
    for field_id in field_ids:
        num = 0
        offset = len(planes) * channels_count * len(timepoints) * field_id
        for time_point in timepoints:
            for channel_id in range(channels_count):
                for plane in planes:
                    if (channel_id in channel_ids) and plane == z_plane and time_point == timepoint:
                        page_num = num + offset
                        pages[page_num] = {
                            'channel_id': channel_id,
                            'time_point': time_point,
                            'plane': plane,
                            'cell': field_id
                        }
                    num = num + 1
    return pages


def extract_xml_schema(xml_info_root):
    full_schema = xml_info_root.tag
    return full_schema[:full_schema.rindex('}') + 1]


def parse_hcs(path, sequence_id):
    with open(path) as json_file:
        data = json.load(json_file)
        preview_dir = data['previewDir']
        time_series_details = data['time_series_details']
        sequences = dict()
        if sequence_id is not None:
            sequences[sequence_id] = time_series_details[sequence_id]
        else:
            sequences = time_series_details
        return preview_dir, sequences


def get_planes_and_channels(path):
    tree = ET.parse(path)
    hcs_xml_info_root = tree.getroot()
    hcs_schema_prefix = extract_xml_schema(hcs_xml_info_root)
    maps = hcs_xml_info_root.find(hcs_schema_prefix + 'Maps').findall(hcs_schema_prefix + 'Map')
    channels = dict()
    for m in maps:
        entries = m.findall(hcs_schema_prefix + 'Entry')
        for e in entries:
            channel_name = e.find(hcs_schema_prefix + 'ChannelName')
            if channel_name is not None:
                channel_id = int(e.attrib.get('ChannelID')) - 1
                channels[channel_id] = channel_name.text
    images = hcs_xml_info_root.find(hcs_schema_prefix + 'Images').findall(hcs_schema_prefix + 'Image')
    planes = []
    for i in images:
        plane_id = i.find(hcs_schema_prefix + 'PlaneID').text
        if plane_id not in planes:
            planes.append(plane_id)
    return channels, planes


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            return False
    return True


def get_selected_channels(params, all_channels):
    selected_channels = dict()
    for channel_id, channel_name in all_channels.items():
        channel_params = params.get(channel_name)
        if channel_params is not None:
            channel_params = channel_params.split(',')
            if len(channel_params) != 5:
                raise RuntimeError("Channel should have 6 parameters")
            selected_channels[channel_id] = {
                'name': channel_name,
                'r': int(channel_params[0]),
                'g': int(channel_params[1]),
                'b': int(channel_params[2]),
                'min': int(channel_params[3]),
                'max': int(channel_params[4])
            }
    if len(selected_channels) < 1:
        raise RuntimeError("At least one channel is required")
    return selected_channels


def get_well_map(preview_seq_dir, well):
    wells_map_path = os.path.join(preview_seq_dir, 'wells_map.json')
    with open(wells_map_path) as json_file:
        data = json.load(json_file)
    well_data = data.get(well, None)
    if not well_data:
        raise RuntimeError("Failed to find well '{}' in wells_map".format(well))
    return well_data


def get_path(preview_seq_dir, original):
    ome_tiff_file_name = 'data.ome.tiff' if original else 'overview_data.ome.tiff'
    offsets_file_name = 'data.offsets.json' if original else 'overview_data.offsets.json'
    ome_tiff_path = os.path.join(preview_seq_dir, ome_tiff_file_name)
    offsets_path = os.path.join(preview_seq_dir, offsets_file_name)
    return ome_tiff_path, offsets_path


def get_paths_from_wells_map(well_map, preview_seq_dir, original=True):
    ome_tiff_path = os.path.join(preview_seq_dir, well_map.get('path' if original else 'overview_path'))
    offsets_path = os.path.join(preview_seq_dir, well_map.get('offsets_path' if original else 'overview_offsets_path'))
    return ome_tiff_path, offsets_path


def get_channel_params(params, channel):
    return next(filter(lambda p: p['channelId'] == channel, params))


def merge_hcs_channels(images):
    result = images[0]
    for i in range(1, len(images)):
        image = images[i]
        r1, g1, b1 = image.split()
        r2, g2, b2 = result.split()
        r = _merge_color_channels(r1, r2)
        g = _merge_color_channels(g1, g2)
        b = _merge_color_channels(b1, b2)
        result = Image.merge("RGB", (Image.fromarray(r), Image.fromarray(g), Image.fromarray(b)))
    return result


def _merge_color_channels(r1, r2):
    r = np.add(np.array(r1, dtype='uint16'), np.array(r2, dtype='uint16'))
    r = np.where(r <= 255, r, 255).astype('uint8')
    return r


def get_image_size(image):
    image_shape = np.shape(image)
    if len(image_shape) != 2:
        raise RuntimeError('Incorrect image')
    return image_shape[0], image_shape[1]


def change_intensity(image, contrast_min, contrast_max):
    image_width, image_height = get_image_size(image)
    image1 = np.repeat(contrast_min, image_width * image_height)
    image1 = np.reshape(image1, (image_width, image_height))
    image = np.subtract(image, image1) / max(0.0005, (contrast_max - contrast_min))
    image = np.where(image <= 0, 0, 255 * image)
    image = np.where(image <= 255, image, 255)
    return image


def color_image(image, channel):
    image = Image.fromarray(change_intensity(image, channel['min'], channel['max']))
    grayscale_image = ImageOps.grayscale(image)
    rgb = (channel['r'], channel['g'], channel['b'])
    colored_image = ImageOps.colorize(grayscale_image, black='black', white=rgb)
    return colored_image


def _get_items(collection, key, target):
    return list((item for item in collection if item.get(key, None) == target))


def get_index_path(preview_dir):
    index_path = os.path.join(preview_dir, 'Index.xml')
    if not os.path.isfile(index_path):
        return os.path.join(preview_dir, 'index.xml')
    return index_path


def calculate_initials_x(coordinates, field_size, image_size_x):
    return calculate_initial_coordinates(coordinates, field_size, image_size_x, 0)


def calculate_initials_y(coordinates, field_size, image_size_y):
    return calculate_initial_coordinates(coordinates, field_size, image_size_y, 1)


def calculate_initial_coordinates(coordinates, field_size, image_size, index):
    values = [coord[index] for coord in list(coordinates.values())]
    start = min(values)
    pixel_size = field_size / image_size
    length = max(values) - start + field_size
    initial_size = int(length / pixel_size) or image_size
    return initial_size, start, pixel_size


def get_array_range(coord, field_size, pixel_size, start):
    initial_coordinate = coord - start
    end = int((initial_coordinate + field_size) / pixel_size)
    start = int(initial_coordinate / pixel_size)
    return start, end


def merge_images_as_arrays(images, field_size, coordinates, initial_size_x, x_start, pixel_size_x,
                           initial_size_y, y_start, pixel_size_y):
    if len(coordinates) == 1:
        for coord_id, coord in coordinates.items():
            return images[coord_id]

    result = np.zeros((initial_size_y, initial_size_x))
    for coord_id, coord in coordinates.items():
        col_start, col_end = get_array_range(coord[0], field_size, pixel_size_x, x_start)
        row_start, row_end = get_array_range(coord[1], field_size, pixel_size_y, y_start)
        result[row_start:row_end, col_start:col_end] = np.flipud(images[coord_id])
    merged_image = np.flipud(result)

    return merged_image


def merge_pages(pages, chanel_ids, timepoint, z_plane, coordinates, field_size):
    merged_pages = list()

    # prepare common initial coordinates
    any_key = list(pages.keys())[0]
    image_array = pages.get(any_key)['image']
    image_size_x, image_size_y = image_array.shape
    initial_size_x, x_start, pixel_size_x = calculate_initials_x(coordinates, field_size, image_size_x)
    initial_size_y, y_start, pixel_size_y = calculate_initials_y(coordinates, field_size, image_size_y)

    # merge cells for each channel
    for chanel_id in chanel_ids:
        image_by_cell = dict()
        chanel_pages = _get_items(pages.values(), 'channel_id', chanel_id)
        [image_by_cell.update({page['cell']: page['image']}) for page in chanel_pages]

        image = merge_images_as_arrays(image_by_cell, field_size, coordinates, initial_size_x, x_start, pixel_size_x,
                                       initial_size_y, y_start, pixel_size_y)

        page = {
            'image': image,
            'channel_id': chanel_id,
            'time_point': timepoint,
            'plane': z_plane
        }
        merged_pages.append(page)
    return merged_pages


def parse_coordinates(well_map, fields):
    coordinates = dict()
    well_map_coordinates = well_map.get('coordinates')
    for key, value in well_map_coordinates.items():
        new_key = int(str(key).lstrip('Image:'))
        if new_key in fields:
            coordinates.update({new_key: value})
    return coordinates


def create_image(params):
    z_plane = get_required_field(params, 'zPlane')
    sequence_id = str(get_required_field(params, 'sequenceId'))
    timepoint = get_required_field(params, 'timepoint')
    image_format = params.get('format', 'tiff')
    path = get_required_field(params, 'path')
    path = prepare_cloud_path(path)
    # field ids
    fields = [int(field) for field in get_required_field(params, 'cells').split(',')]
    # well: shall have format as in wells_map.json col_row (example: 2_2)
    wells_map_key = get_required_field(params, 'well')

    preview_dir, sequences = parse_hcs(path, sequence_id)
    timepoints = sequences[sequence_id]
    preview_dir = prepare_cloud_path(preview_dir)
    index_path = get_index_path(preview_dir)
    # hcs channels and z-planes
    channels, planes = get_planes_and_channels(index_path)
    if str(z_plane) not in [str(plane) for plane in planes]:
        raise RuntimeError('Incorrect Z plane id [{}]'.format(z_plane))
    selected_channels = get_selected_channels(params, channels)
    image_path = build_image_path(image_format, z_plane, selected_channels, timepoint, sequence_id, fields)

    preview_seq_dir = os.path.join(preview_dir, '{}'.format(sequence_id))
    ome_tiff_path, offsets_path = get_path(preview_seq_dir, True)
    well_map = get_well_map(preview_seq_dir, wells_map_key)
    if not os.path.isfile(ome_tiff_path):
        ome_tiff_path, offsets_path = get_paths_from_wells_map(well_map, preview_seq_dir)
    coordinates = parse_coordinates(well_map, fields)
    field_size = well_map.get('field_size')

    pages = get_pages_for_fields(list(selected_channels.keys()), z_plane, planes, len(channels), timepoint, timepoints,
                                 fields)
    set_offsets(offsets_path, pages)
    read_images(ome_tiff_path, pages)

    pages = merge_pages(pages, selected_channels.keys(), timepoint, z_plane, coordinates, field_size)
    colored_images = []
    for page in pages:
        channel_id = page['channel_id']
        colored_image = color_image(page['image'], selected_channels[channel_id])
        colored_images.append(colored_image)
    merged_image = merge_hcs_channels(colored_images)
    merged_image.save(image_path)

    return image_path
