# Way to provide specific logic about transitioning files to specific STORAGE_CLASS.
# f.e. Transitioning to GLACIER_IR will be applied only for file that is bigger that 128kb, according to AWS docs,
#      so, we need to filter such files out
#
# This filter applying in both situation, when actual archiving action is performed
# and when we check the result of the existing actions
def get_storage_class_specific_file_filter(target_storage_class):
    if target_storage_class == "GLACIER_IR":
        # Docs: https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html
        # Files with size smaller that 128kb will not be moved from the S3 Standard or
        # S3 Standard-IA storage classes to S3 Intelligent-Tiering or S3 Glacier Instant Retrieval
        return lambda f: f.size > 128 * 1024
    else:
        return lambda f: True
