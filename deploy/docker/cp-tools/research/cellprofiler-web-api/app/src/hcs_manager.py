from .config import Config
from .hcs_pipeline import ImageCoords, HcsPipeline, PipelineState
from multiprocessing import Process
from time import sleep
import glob
import os
from .z_planes_pipeline import ZPlanesPipeline
from tifffile import imread
import numpy as np
from moviepy.editor import ImageSequenceClip
import time
from PIL import Image, ImageOps
import xml.etree.ElementTree as ET
import json
import errno

CELLPROFILER_API_COMMON_RESULTS_DIR = os.getenv('CELLPROFILER_API_COMMON_RESULTS_DIR')
HCS_CLOUD_FILES_SCHEMA = os.getenv('HCS_PARSING_CLOUD_FILES_SCHEMA', 's3')


class HCSManager:

    def __init__(self, pipelines):
        self.pipelines = pipelines
        self.running_processes = {}
        self.queue = list()

    def create_pipeline(self, measurement_uuid):
        pipeline = HcsPipeline(measurement_uuid)
        pipeline_id = pipeline.get_id()
        self.pipelines.update({pipeline_id: pipeline})
        return pipeline_id

    def get_pipeline(self, pipeline_id):
        pipeline = self._get_pipeline(pipeline_id)
        return pipeline.get_structure()

    def add_files(self, pipeline_id: int, input_data: dict):
        pipeline = self._get_pipeline(pipeline_id)
        files_data = self._get_required_field(input_data, 'files')
        z_planes = input_data.get('zPlanes', None)
        bit_depth = input_data.get('bitDepth', None)
        coordinates_list = [self._parse_inputs(coordinates) for coordinates in files_data]
        pipeline.set_input(coordinates_list)
        if z_planes:
            self.squash_z_planes(pipeline_id, z_planes, files_data, bit_depth)

    def create_module(self, pipeline_id, module_data):
        pipeline = self._get_pipeline(pipeline_id)
        module_name = self._get_required_field(module_data, 'moduleName')
        module_id = self._get_required_field(module_data, 'moduleId')
        module_config = module_data.get('parameters')
        pipeline.add_module(module_name, module_id, module_config)

    def update_module(self, pipeline_id, module_id, module_data):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.edit_module(int(module_id), module_data)

    def move_module(self, pipeline_id, module_id, direction):
        pipeline = self._get_pipeline(pipeline_id)
        if not (direction == 'up' or direction == 'down'):
            raise RuntimeError("Direction value must be equal 'up' or 'down'")
        pipeline.move_module(module_id, direction)

    def delete_module(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.remove_module(module_id)

    def run_module(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.run_module(module_id)

    def get_status(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        if not module_id:
            return pipeline.get_pipeline_status().to_json()
        return pipeline.get_module_status(module_id).to_json()

    def _get_pipeline(self, pipeline_id):
        pipeline = self.pipelines.get(pipeline_id)
        if not pipeline:
            raise RuntimeError("Failed to find pipeline '%s'" % pipeline_id)
        return pipeline

    def squash_z_planes(self, parent_pipeline_id: int, z_planes: list, files_data: list, bit_depth: str = None):
        parent_pipeline = self._get_pipeline(parent_pipeline_id)

        projection_pipeline = ZPlanesPipeline(parent_pipeline.get_measurement(), bit_depth)
        pipeline_id = projection_pipeline.get_id()
        self.pipelines.update({pipeline_id: projection_pipeline})
        parent_pipeline.set_pre_processing_pipeline(pipeline_id)

        projection_pipeline.set_z_planes(z_planes)
        coordinates = [self._parse_inputs(coordinates) for coordinates in files_data]
        projection_pipeline.set_input(coordinates)

        if projection_pipeline.is_empty():
            parent_pipeline.set_pre_processing_pipeline(None)
            parent_pipeline.set_z_planes(None)
            return
        parent_pipeline.set_z_planes(z_planes)

    def launch_pipeline(self, pipeline_id: int):
        parent_pipeline = self._get_pipeline(pipeline_id)
        pre_processing_pipeline = parent_pipeline.get_pre_processing_pipeline()
        if pre_processing_pipeline is not None:
            self._run_projection_pipeline(pre_processing_pipeline, parent_pipeline)
        self._run_pipeline(pipeline_id)

    def _run_pipeline(self, pipeline_id, parent_pipeline=None):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.set_pipeline_state(PipelineState.CONFIGURING)
        delay = int(Config.RUN_DELAY)
        pool_size = int(Config.POOL_SIZE)
        try:
            while True:
                available_processors = pool_size - len(self.running_processes)
                if available_processors > 0:
                    if len(self.queue) == 0 or self.queue[0] == pipeline_id:
                        if len(self.queue) != 0:
                            self.queue.remove(pipeline_id)
                        self._set_pipeline_state(pipeline, PipelineState.RUNNING, parent_pipeline)
                        process = Process(target=pipeline.run_pipeline)
                        process.start()
                        print("[DEBUG] Run process '%s' started with PID %d" % (pipeline_id, process.pid))
                        self.running_processes.update({pipeline_id: process})
                        process.join()
                        print("[DEBUG] Run process '%s' finished" % pipeline_id)
                        pipeline.set_pipeline_state(PipelineState.FINISHED)
                        self.running_processes.pop(pipeline_id)
                        return
                if not self.queue.__contains__(pipeline_id):
                    self._set_pipeline_state(pipeline, PipelineState.QUEUED, parent_pipeline)
                    self.queue.append(pipeline_id)
                    print("[DEBUG] Run '%s' queued" % pipeline_id)
                sleep(delay)
        except BaseException as e:
            self._set_pipeline_state(pipeline, PipelineState.FAILED, parent_pipeline, message=str(e))
            raise e

    def _run_projection_pipeline(self, projection_pipeline_id: int, parent_pipeline: HcsPipeline):
        projection_pipeline = self._get_pipeline(projection_pipeline_id)
        if projection_pipeline.is_empty():
            print("[DEBUG] No need to run projection pipeline.")
            return
        self._run_pipeline(projection_pipeline_id, parent_pipeline)
        parent_pipeline.set_pipeline_files(self._collect_parent_pipeline_inputs(parent_pipeline, projection_pipeline))
        parent_pipeline.set_input_sets()

    def _parse_inputs(self, coordinates):
        x = int(self._get_required_field(coordinates, 'x'))
        y = int(self._get_required_field(coordinates, 'y'))
        z = int(self._get_required_field(coordinates, 'z'))
        field_id = int(self._get_required_field(coordinates, 'fieldId'))
        time_point = int(self._get_required_field(coordinates, 'timepoint'))
        channel = int(coordinates.get('channel', 1))
        channel_name = coordinates.get('channelName', 'DAPI')
        return ImageCoords(x, y, time_point, z, field_id, channel, channel_name)

    def _collect_parent_pipeline_inputs(self, parent_pipeline: HcsPipeline, projection_pipeline: ZPlanesPipeline):
        parent_pipeline_inputs = self._collect_pipeline_tiff_outputs(projection_pipeline.get_id(),
                                                                     parent_pipeline.get_measurement())
        for image in projection_pipeline.parent_pipeline_inputs:
            parent_pipeline_inputs.append(parent_pipeline.map_to_file_name(image))
        return parent_pipeline_inputs

    def create_clip(self, params):
        t = time.time()

        clip_format = params.get('format') if 'format' in params else '.webm'
        codec = params.get('codec') if 'codec' in params else ('mpeg4' if clip_format == '.mp4' else None)
        fps = int(params.get('fps')) if 'fps' in params else 1
        duration = float(params.get('duration')) if 'duration' in params else 1
        if duration < 1:
            raise RuntimeError('Duration should be >= 1')
        sequence_id = params.get('sequenceId') if 'sequenceId' in params else None
        plane_id = params.get('planeId') if 'planeId' in params else '1'

        path = self._get_required_field(params, 'path')
        path = self.extract_local_path(path)
        by_field = int(self._get_required_field(params, 'byField'))
        cell = int(self._get_required_field(params, 'cell'))

        preview_dir, sequences = self.parse_hcs(path, sequence_id)
        preview_dir = self.extract_local_path(preview_dir)
        index_path = preview_dir + '/index.xml'
        all_channels = self.get_all_channels(index_path)
        planes = self.get_planes(index_path)
        if plane_id not in planes:
            raise RuntimeError('Incorrect Z plane id [{}]'.format(plane_id))
        channels_num = len(all_channels)
        selected_channel_ids, selected_channels = self.get_selected_channels(params, all_channels)

        clips = []
        durations = []
        for seq in sequences.keys():
            timepoints = len(sequences[seq])
            ome_tiff_path = self.get_ome_tiff_path(preview_dir, seq, by_field)
            pages = self.get_pages(selected_channel_ids, plane_id, planes, channels_num, timepoints, cell)
            images = self.read_images(ome_tiff_path, pages)
            curr = 0
            for time_point in range(timepoints):
                channel_images = []
                for channel in selected_channels:
                    channel_image = images[curr]
                    colored_image = self.color_image(channel_image, channel)
                    channel_images.append(colored_image)
                    curr = curr + 1
                merged_image = self.merge_channels(channel_images)
                clips.append(np.array(merged_image))
                durations.append(duration)

        clip_path = CELLPROFILER_API_COMMON_RESULTS_DIR + self.get_clip_file_dir(path)
        self._mkdir(clip_path)
        clip_name = self.get_clip_file_name(by_field, cell, clip_format, sequence_id, plane_id)
        clip_full_path = clip_path + clip_name
        slide = ImageSequenceClip(clips, durations=durations)
        slide.write_videofile(clip_full_path, codec=codec, fps=fps)
        t1 = time.time()
        return clip_full_path, round((t1 - t), 2)

    @staticmethod
    def read_images(ome_tiff_path, pages):
        images = imread(ome_tiff_path, key=pages)
        if len(pages) == 1:
            images = np.reshape(images, (1, 1080, 1080))
        return images

    @staticmethod
    def get_clip_file_dir(path):
        hcs_file_base_name = os.path.basename(path)
        hcs_file_name = os.path.splitext(hcs_file_base_name)[0]
        return '/video/{}/'.format(hcs_file_name)

    @staticmethod
    def get_clip_file_name(by_field, cell, clip_format, sequence_id, plane_id):
        cell_prefix = ('field{}' if by_field else '/well{}').format(str(cell))
        plane_prefix = 'plane{}'.format(plane_id)
        sequence_prefix = ('' if sequence_id is None else 'seq{}').format(sequence_id)
        return cell_prefix + sequence_prefix + plane_prefix + clip_format

    @staticmethod
    def get_pages(channel_ids, plane_id, planes, channels, timepoints, cell):
        pages = []
        num = 0
        for time_point in range(timepoints):
            for plane in planes:
                for channel_id in range(channels):
                    if (channel_id in channel_ids) and (plane == plane_id):
                        pages.append(num + len(planes) * channels * timepoints * cell)
                    num = num + 1
        return pages

    @staticmethod
    def extract_xml_schema(xml_info_root):
        full_schema = xml_info_root.tag
        return full_schema[:full_schema.rindex('}') + 1]

    @staticmethod
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

    def get_all_channels(self, path):
        tree = ET.parse(path)
        hcs_xml_info_root = tree.getroot()
        hcs_schema_prefix = self.extract_xml_schema(hcs_xml_info_root)
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

    def get_planes(self, path):
        tree = ET.parse(path)
        hcs_xml_info_root = tree.getroot()
        hcs_schema_prefix = self.extract_xml_schema(hcs_xml_info_root)
        images = hcs_xml_info_root.find(hcs_schema_prefix + 'Images').findall(hcs_schema_prefix + 'Image')
        planes = []
        for i in images:
            plane_id = i.find(hcs_schema_prefix + 'PlaneID').text
            if plane_id not in planes:
                planes.append(plane_id)
        return planes

    @staticmethod
    def extract_local_path(path, cloud_scheme=HCS_CLOUD_FILES_SCHEMA):
        path_chunks = path.split(cloud_scheme + '://', 1)
        if len(path_chunks) != 2:
            raise RuntimeError('Unable to determine local path of [{}]'.format(path))
        return '{}/{}'.format('/cloud-data', path_chunks[1])

    @staticmethod
    def _mkdir(path):
        try:
            os.makedirs(path)
        except OSError as e:
            if e.errno == errno.EEXIST and os.path.isdir(path):
                pass
            else:
                return False
        return True

    @staticmethod
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

    @staticmethod
    def get_ome_tiff_path(preview_dir, seq, by_field):
        return preview_dir + '/{}/'.format(seq) + ('data.ome.tiff' if by_field else 'overview_data.ome.tiff')

    @staticmethod
    def get_channel_params(params, channel):
        return next(filter(lambda p: p['channelId'] == channel, params))

    @staticmethod
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

    @staticmethod
    def change_intensity(intensity, contrast_min, contrast_max):
        return (intensity - contrast_min) / max(0.0005, (contrast_max - contrast_min))

    def color_image(self, image, channel):
        image = self.change_intensity(image, channel['min'], channel['max'])
        image = np.where(image <= 0, 0, 255 * image)
        image = np.where(image <= 255, image, 255)
        image = Image.fromarray(image)
        grayscale_image = ImageOps.grayscale(image)
        rgb = (channel['r'], channel['g'], channel['b'])
        colored_image = ImageOps.colorize(grayscale_image, black='black', white=rgb)
        return colored_image

    @staticmethod
    def _get_required_field(json_data, field_name):
        field_value = json_data.get(field_name)
        if field_value is None:
            raise RuntimeError("Field '%s' is required" % field_name)
        return field_value

    @staticmethod
    def _collect_pipeline_tiff_outputs(pipeline_id, measurements_uuid):
        results = list()
        results_dir = os.path.join(Config.COMMON_RESULTS_DIR, measurements_uuid, pipeline_id)
        for tiff_file_path in glob.iglob(results_dir + '/**/*.tiff', recursive=True):
            results.append(tiff_file_path)
        return results

    @staticmethod
    def _set_pipeline_state(current_pipeline, pipeline_state, parent_pipeline=None, message=''):
        current_pipeline.set_pipeline_state(pipeline_state, message=message)
        if parent_pipeline:
            parent_pipeline.set_pipeline_state(pipeline_state, message=message)
