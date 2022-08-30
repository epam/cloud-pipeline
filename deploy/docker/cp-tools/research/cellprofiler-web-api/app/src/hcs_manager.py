from .config import Config
from .hcs_pipeline import ImageCoords, HcsPipeline, PipelineState
from multiprocessing import Process
from time import sleep
from tifffile import imread
import numpy as np
from moviepy.editor import ImageSequenceClip
import time
from PIL import Image, ImageOps


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

    def add_files(self, pipeline_id, files_data):
        pipeline = self._get_pipeline(pipeline_id)
        coordinates_list = list()
        for coordinates in files_data:
            x = int(self._get_required_field(coordinates, 'x'))
            y = int(self._get_required_field(coordinates, 'y'))
            z = int(self._get_required_field(coordinates, 'z'))
            field_id = int(self._get_required_field(coordinates, 'fieldId'))
            time_point = int(self._get_required_field(coordinates, 'timepoint'))
            channel = int(coordinates.get('channel', 1))
            channel_name = coordinates.get('channelName', 'DAPI')
            coordinates_list.append(ImageCoords(x, y, time_point, z, field_id, channel, channel_name))
        pipeline.set_input(coordinates_list)

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

    def run_pipeline(self, pipeline_id):
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
                        pipeline.set_pipeline_state(PipelineState.RUNNING)
                        process = Process(target=pipeline.run_pipeline)
                        process.start()
                        print("[DEBUG] Run process '%s' started with PID %d" % (pipeline_id, process.pid))
                        self.running_processes.update({pipeline_id: process})
                        process.join()
                        print("[DEBUG] Run processes '%s' finished" % pipeline_id)
                        pipeline.set_pipeline_state(PipelineState.FINISHED)
                        self.running_processes.pop(pipeline_id)
                        return
                if not self.queue.__contains__(pipeline_id):
                    pipeline.set_pipeline_state(PipelineState.QUEUED)
                    self.queue.append(pipeline_id)
                    print("[DEBUG] Run '%s' queued" % pipeline_id)
                sleep(delay)
        except BaseException as e:
            error_description = str(e)
            pipeline.set_pipeline_state(PipelineState.FAILED, message=error_description)
            raise e

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

    def create_clip(self, params):
        t = time.time()

        clip_format = params.get('format') if 'format' in params else '.webm'
        codec = params.get('codec') if 'codec' in params else None
        fps = int(params.get('fps')) if 'fps' in params else 1
        ome_tiff_dir = self._get_required_field(params, 'path')
        by_field = int(self._get_required_field(params, 'byField'))
        chs = int(self._get_required_field(params, 'chs'))
        tps = int(self._get_required_field(params, 'tps'))
        cell = int(self._get_required_field(params, 'cell'))

        clip_dir = ome_tiff_dir
        clip_file_name = ('field_' if by_field else 'well_') + str(cell)
        ome_tiff = self.get_ome_tiff_path(ome_tiff_dir, by_field)

        channel_ids, channels = self.get_channels(chs, params)

        images = imread(ome_tiff, key=self.get_pages(channel_ids, chs, tps, cell))
        clips = []
        curr = 0
        for time_point in range(tps):
            channel_images = []
            for channel in channel_ids:
                channel_image = images[curr]
                channel_params = next(filter(lambda c: c['id'] == channel, channels))
                colored_image = self.color_image(channel_image, channel_params)
                channel_images.append(colored_image)
                curr = curr + 1
            merged_image = self.merge_channels(channel_images)
            clips.append(np.array(merged_image))
        slide = ImageSequenceClip(clips, fps)
        slide.write_videofile(clip_dir + clip_file_name + clip_format, codec=codec)
        t1 = time.time()
        return round((t1 - t), 2)

    @staticmethod
    def get_ome_tiff_path(ome_tiff_dir, by_field):
        return ome_tiff_dir + ('data' if by_field else 'overview_data') + '.ome.tiff'

    @staticmethod
    def get_pages(channel_ids, channels, timepoints, cell):
        pages = []
        num = 0
        for time_point in range(timepoints):
            for channel_id in range(channels):
                if channel_id in channel_ids:
                    pages.append(num + channels * timepoints * cell)
                num = num + 1
        return pages

    @staticmethod
    def get_channels(chs, params):
        channels = []
        channel_ids = []
        for chl in range(chs):
            channel_params = params.get('ch' + str(chl))
            if channel_params is not None:
                channel_params = channel_params.split(',')
                if len(channel_params) != 6:
                    raise RuntimeError("Channel should have 6 parameters")
                channel = dict()
                channel['id'] = int(channel_params[0])
                channel['r'] = int(channel_params[1])
                channel['g'] = int(channel_params[2])
                channel['b'] = int(channel_params[3])
                channel['min'] = int(channel_params[4])
                channel['max'] = int(channel_params[5])
                channel_ids.append(int(channel_params[0]))
                channels.append(channel)
        if len(channels) < 1:
            raise RuntimeError("At least one channel is required")
        return channel_ids, channels

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

    def color_image(self, image, params):
        image = self.change_intensity(image, params['min'], params['max'])
        image = np.where(image <= 0, 0, 255 * image)
        image = np.where(image <= 255, image, 255)
        image = Image.fromarray(image)
        grayscale_image = ImageOps.grayscale(image)
        rgb = (params['r'], params['g'], params['b'])
        colored_image = ImageOps.colorize(grayscale_image, black='black', white=rgb)
        return colored_image

    @staticmethod
    def _get_required_field(json_data, field_name):
        field_value = json_data.get(field_name)
        if field_value is None:
            raise RuntimeError("Field '%s' is required" % field_name)
        return field_value
