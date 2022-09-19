from .config import Config
import os
from tifffile import imread
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
    fps = int(params.get('fps')) if 'fps' in params else 1
    duration = float(params.get('duration')) if 'duration' in params else 1
    if duration < 1:
        raise RuntimeError('Duration should be >= 1')
    sequence_id = params.get('sequenceId') if 'sequenceId' in params else None
    plane_id = params.get('planeId') if 'planeId' in params else '1'

    path = HCSManager.get_required_field(params, 'path')
    path = prepare_input_path(path)
    by_field = int(HCSManager.get_required_field(params, 'byField'))
    cell = int(HCSManager.get_required_field(params, 'cell'))

    preview_dir, sequences = parse_hcs(path, sequence_id)
    preview_dir = prepare_input_path(preview_dir)
    index_path = os.path.join(preview_dir, 'Index.xml')
    if not os.path.isfile(index_path):
        index_path = os.path.join(preview_dir, 'index.xml')
    all_channels = get_all_channels(index_path)
    planes = get_planes(index_path)
    if plane_id not in planes:
        raise RuntimeError('Incorrect Z plane id [{}]'.format(plane_id))
    channels_num = len(all_channels)
    selected_channel_ids, selected_channels = get_selected_channels(params, all_channels)

    clips = []
    durations = []
    for seq in sequences.keys():
        timepoints = len(sequences[seq])
        ome_tiff_path = get_ome_tiff_path(preview_dir, seq, by_field)
        pages = get_pages(selected_channel_ids, plane_id, planes, channels_num, timepoints, cell)
        images = read_images(ome_tiff_path, pages)
        curr = 0
        for time_point in range(timepoints):
            channel_images = []
            for channel in selected_channels:
                channel_image = images[curr]
                colored_image = color_image(channel_image, channel)
                channel_images.append(colored_image)
                curr = curr + 1
            merged_image = merge_channels(channel_images)
            clips.append(np.array(merged_image))
            durations.append(duration)

    clip_name = get_clip_name(by_field, cell, clip_format, sequence_id, plane_id)
    slide = ImageSequenceClip(clips, durations=durations)
    slide.write_videofile(clip_name, codec=codec, fps=fps)
    t1 = time.time()
    return clip_name, round((t1 - t), 2)


def read_images(ome_tiff_path, pages):
    images = imread(ome_tiff_path, key=pages)
    if len(pages) == 1:
        image_width, image_height = get_image_size(images)
        images = np.reshape(images, (1, image_width, image_height))
    return images


def get_clip_name(by_field, cell, clip_format, sequence_id, plane_id):
    clip_dir = os.path.join(Config.COMMON_RESULTS_DIR, 'video', str(uuid.uuid4()))
    mkdir(clip_dir)
    cell_prefix = ('field{}' if by_field else 'well{}').format(str(cell))
    plane_prefix = 'plane{}'.format(plane_id)
    sequence_prefix = '' if sequence_id is None else 'seq{}'.format(sequence_id)
    clip_name = cell_prefix + sequence_prefix + plane_prefix + clip_format
    return os.path.join(clip_dir, clip_name)


def get_pages(channel_ids, plane_id, planes, channels, timepoints, cell):
    pages = []
    num = 0
    for time_point in range(timepoints):
        for channel_id in range(channels):
            for plane in planes:
                if (channel_id in channel_ids) and (plane == plane_id):
                    pages.append(num + len(planes) * channels * timepoints * cell)
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


def get_all_channels(path):
    tree = ET.parse(path)
    hcs_xml_info_root = tree.getroot()
    hcs_schema_prefix = extract_xml_schema(hcs_xml_info_root)
    maps = hcs_xml_info_root.find(hcs_schema_prefix + 'Maps').findall(hcs_schema_prefix + 'Map')
    channels = []
    for m in maps:
        entries = m.findall(hcs_schema_prefix + 'Entry')
        for e in entries:
            channel_name = e.find(hcs_schema_prefix + 'ChannelName')
            if channel_name is not None:
                channel = dict()
                channel['id'] = int(e.attrib.get('ChannelID')) - 1
                channel['name'] = channel_name.text
                channels.append(channel)
    return channels


def get_planes(path):
    tree = ET.parse(path)
    hcs_xml_info_root = tree.getroot()
    hcs_schema_prefix = extract_xml_schema(hcs_xml_info_root)
    images = hcs_xml_info_root.find(hcs_schema_prefix + 'Images').findall(hcs_schema_prefix + 'Image')
    planes = []
    for i in images:
        plane_id = i.find(hcs_schema_prefix + 'PlaneID').text
        if plane_id not in planes:
            planes.append(plane_id)
    return planes


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            return False
    return True


def get_selected_channels(params, channels):
    selected_channels = []
    selected_channel_ids = []
    for chl in channels:
        channel_params = params.get(chl['name'])
        if channel_params is not None:
            channel_params = channel_params.split(',')
            if len(channel_params) != 5:
                raise RuntimeError("Channel should have 6 parameters")
            channel = dict()
            channel['id'] = chl['id']
            channel['name'] = chl['name']
            channel['r'] = int(channel_params[0])
            channel['g'] = int(channel_params[1])
            channel['b'] = int(channel_params[2])
            channel['min'] = int(channel_params[3])
            channel['max'] = int(channel_params[4])
            selected_channel_ids.append(chl['id'])
            selected_channels.append(channel)
    if len(selected_channels) < 1:
        raise RuntimeError("At least one channel is required")
    return selected_channel_ids, selected_channels


def get_ome_tiff_path(preview_dir, seq, by_field):
    return os.path.join(preview_dir, '{}'.format(seq), 'data.ome.tiff' if by_field else 'overview_data.ome.tiff')


def get_channel_params(params, channel):
    return next(filter(lambda p: p['channelId'] == channel, params))


def merge_channels(images):
    result = images[0]
    for i in range(1, len(images)):
        image = images[i]
        r1, g1, b1 = image.split()
        r2, g2, b2 = result.split()
        r = np.minimum(255, np.add(np.array(r1), np.array(r2)))
        g = np.minimum(255, np.add(np.array(g1), np.array(g2)))
        b = np.minimum(255, np.add(np.array(b1), np.array(b2)))
        result = Image.merge("RGB", (Image.fromarray(r), Image.fromarray(g), Image.fromarray(b)))
    return result


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
