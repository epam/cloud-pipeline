import llama_index.core
from cpaibot.common.logger import CpLogger


agents_logger = CpLogger('cpaibot')
llama_index.core.set_global_handler('simple',
                                    logger=agents_logger)
