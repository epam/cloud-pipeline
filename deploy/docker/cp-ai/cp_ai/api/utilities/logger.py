from cp_ai.common.logger import create_logger
from cp_ai.common.settings import cp_ai_settings


api_logger = create_logger('AI', file_path=cp_ai_settings.cp_api_logs_file)
