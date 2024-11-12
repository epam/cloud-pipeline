import math
import sys
from enum import Enum


class HcsRootType(Enum):
    TIFF = 1
    CZI  = 2

    @staticmethod
    def get(name):
        if name.lower() == HcsRootType.CZI.name.lower():
            return HcsRootType.CZI
        else:
            return HcsRootType.TIFF


class HcsRoot:
    def __init__(self, root_path, hcs_img_path):
        self.root_path = root_path
        self.hcs_img_path = hcs_img_path


class FieldDetails:
    def __init__(self, well_column, well_row, ome_image_id, x, y):
        self.well_column = int(well_column)
        self.well_row = int(well_row)
        self.ome_image_id = ome_image_id
        self.x = float(x)
        self.y = float(y)


class WellGrid:
    def __init__(self):
        self.__x_coords = set()
        self.__y_coords = set()
        self.__fields = set()
        self.__height = None
        self.__width = None
        self.__field_size_y = None
        self.__field_size_x = None

    def add_x_coord(self, value):
        self.__x_coords.add(value)

    def add_y_coord(self, value):
        self.__y_coords.add(value)

    def add_field(self, field):
        self.__fields.add(field)

    def get_width(self):
        return self.__width

    def set_width(self, value):
        self.__width = value

    def calculate_width(self, size, resolution):
        x_min = sys.maxsize
        x_max = -sys.maxsize - 1
        field_size = size * resolution
        self.__field_size_x = field_size
        for field in self.__fields:
            if field[0] < x_min:
                x_min = field[0]
            if field[0] + field_size > x_max:
                x_max = field[0] + field_size
        return math.ceil((x_max - x_min) / field_size)

    def get_height(self):
        return self.__height

    def set_height(self, value):
        self.__height = value

    def calculate_height(self, size, resolution):
        y_min = sys.maxsize
        y_max = -sys.maxsize - 1
        field_size = size * resolution
        self.__field_size_y = field_size
        for field in self.__fields:
            if field[1] < y_min:
                y_min = field[1]
            if field[1] + field_size > y_max:
                y_max = field[1] + field_size
        return math.ceil((y_max - y_min) / field_size)

    def get_values_dict(self):
        return dict({y_coord: set(self.__x_coords) for y_coord in self.__y_coords})

    def get_field_size(self):
        return self.__field_size_y