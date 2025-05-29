import argparse
import logging
from documents_index import create_index

default_logger = logging.getLogger(name="AI")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", default=True)
    args = parser.parse_args()
    default_logger.info("Creating index,,,")
    print("Creating index...")
    create_index(args.force)

if __name__ == '__main__':
    main()