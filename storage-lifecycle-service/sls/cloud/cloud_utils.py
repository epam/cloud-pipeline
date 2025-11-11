# Way to provide specific logic about transitioning files to specific STORAGE_CLASS.
# f.e. Transitioning to GLACIER_IR will be applied only for file that is bigger that 128kb, according to AWS docs,
#      so, we need to filter such files out
#
# This filter applying in both situation, when actual archiving action is performed
# and when we check the result of the existing actions
DEFAULT_MIN_SIZE_OF_OBJECT_TO_TRANSIT = 128 * 1024
