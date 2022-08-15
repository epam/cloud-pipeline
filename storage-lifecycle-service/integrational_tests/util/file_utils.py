import random
import string


def generate_object_content(length):
    return "".join([random.choice(string.ascii_letters) for _ in range(length)])
