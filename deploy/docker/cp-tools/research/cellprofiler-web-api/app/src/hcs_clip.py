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
from .hcs_modules_factory import prepare_input_path
from .hcs_manager import HCSManager
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

    path = HCSManager.get_required_field(params, 'path')
    path = prepare_input_path(path)
    # by_field = 1 - create video for specified well field
    # by_field = 0 - create video for specified well
    by_field = int(HCSManager.get_required_field(params, 'byField'))
    # well or well field id
    cell = int(HCSManager.get_required_field(params, 'cell'))

    preview_dir, sequences = parse_hcs(path, sequence_id)
    preview_dir = prepare_input_path(preview_dir)
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
        ome_tiff_path, offsets_path = get_path(preview_dir, seq, by_field)
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


def get_path(preview_dir, seq, by_field):
    ome_tiff_file_name = 'data.ome.tiff' if by_field else 'overview_data.ome.tiff'
    offsets_file_name = 'data.offsets.json' if by_field else 'overview_data.offsets.json'
    preview_seq_dir = os.path.join(preview_dir, '{}'.format(seq))
    ome_tiff_path = os.path.join(preview_seq_dir, ome_tiff_file_name)
    offsets_path = os.path.join(preview_seq_dir, offsets_file_name)
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
