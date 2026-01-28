import logging
import os

from dotenv import load_dotenv

load_dotenv()
logger_format = '[%(asctime)s.%(msecs)03d] %(levelname)-8s [%(name)s] %(message)s'
logger_level = 'INFO'

logging.basicConfig(format=logger_format,
                    datefmt="%Y-%m-%d %H:%M:%S",)


def create_logger(name: str, file_name: str | None = None) -> logging.Logger:
    logger = logging.getLogger(name)
    logger.setLevel(logger_level)
    if file_name:
        os.makedirs(os.path.dirname(file_name), exist_ok=True)
        file_handler = logging.FileHandler(file_name)
        file_handler.setLevel(logger_level)
        formatter = logging.Formatter('[%(asctime)s.%(msecs)03d] %(levelname)-8s %(message)s',
                                      datefmt="%Y-%m-%d %H:%M:%S")
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)
    return logger
