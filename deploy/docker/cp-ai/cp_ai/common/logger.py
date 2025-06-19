import logging
import os
import sys
from logging import Logger

def create_logger(name: str,
                  /,
                  file_path: str | None = None,
                  level = logging.DEBUG) -> Logger:
    logging_level = level

    logging_format = '%(asctime)s:%(levelname)s: %(message)s'
    logging_formatter = logging.Formatter(logging_format)
    logger = logging.getLogger(name=name)
    logger.setLevel(logging_level)

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging_level)
    console_handler.setFormatter(logging_formatter)
    logger.addHandler(console_handler)

    if file_path is not None:
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        file_handler = logging.FileHandler(file_path)
        file_handler.setLevel(logging_level)
        file_handler.setFormatter(logging_formatter)
        logger.addHandler(file_handler)

    return logger
