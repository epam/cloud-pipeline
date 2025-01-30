#!/bin/bash
# ngs-portal build script
# usage: build-ngs-portal.sh --image ${NGS_PORTAL_IMAGE:-ngs-portal} --version ${NGS_PORTAL_VERSION:-latest}
#
# Default values
NGS_PORTAL_IMAGE="${NGS_PORTAL_IMAGE:-ngs-portal}"
NGS_PORTAL_VERSION="${NGS_PORTAL_VERSION:-}"
NGS_PORTAL_COMMIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
PUSH_IMAGES=false
TAG_AS_LATEST=false

# Display help
function display_help() {
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Build a Docker image for the ngs-portal app."
  echo ""
  echo "Options:"
  echo "  --image <image-name>   Specify the image name (default: ngs-portal)"
  echo "  --version <version>    Specify the version tag (default: commit SHA if not provided)"
  echo "  --push                 Push the built images to the registry (default: false)"
  echo "  --latest               Tag as latest (default: true if version is not specified)"
  echo "  --help                 Show this help message and exit"
  echo ""
  echo "Examples:"
  echo "  $0                     Build with default image name 'ngs-portal' and tag '<commit-sha>'"
  echo "  $0 --image my-image    Build with the specified image name and tag '<commit-sha>'"
  echo "  $0 --version abcde     Build with default image name and specified version 'abcde'"
  echo "  $0 --image my-image --version abcde"
  echo "                         Build with specified image name and version"
  echo "  $0 --push              Build and push images with default settings"
  echo ""
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --image)
      NGS_PORTAL_IMAGE="$2"
      shift # Shift past the argument
      shift # Shift past the value
      ;;
    --version)
      NGS_PORTAL_VERSION="$2"
      shift
      shift
      ;;
    --push)
      PUSH_IMAGES=true
      shift
      ;;
    --latest)
      TAG_AS_LATEST=true
    --help)
      display_help
      exit 0
      ;;
    *)
      echo "Invalid option: $1"
      echo "Use --help to see usage instructions."
      exit 1
      ;;
  esac
done

# Determine the version tag
if [[ -z "$NGS_PORTAL_VERSION" ]]; then
  if [[ "$NGS_PORTAL_COMMIT_SHA" == "unknown" ]]; then
    # If the commit SHA is unavailable and no version is specified, tag only as "latest"
    echo "Could not determine commit SHA. Using 'latest' as the version."
    NGS_PORTAL_VERSION="latest"
    LATEST_TAG=false
  else
    # If no version is specified but commit SHA is available, use it and also tag "latest"
    echo "No version specified. Using commit SHA: $NGS_PORTAL_COMMIT_SHA as version."
    NGS_PORTAL_VERSION="$NGS_PORTAL_COMMIT_SHA"
    LATEST_TAG=true
  fi
else
  # If a version is specified, only create the specified version tag
  LATEST_TAG=false
fi

if [[ "$TAG_AS_LATEST" == true ]]; then
  LATEST_TAG=true
fi

if [[ "$NGS_PORTAL_VERSION" == "latest" ]]; then
  LATEST_TAG=false
fi

# Log details
echo "Building NGS-Portal docker image:"
echo "  Image: $NGS_PORTAL_IMAGE"
echo "  Version: $NGS_PORTAL_VERSION"
echo "  Commit SHA: $NGS_PORTAL_COMMIT_SHA"
if [[ "$LATEST_TAG" == true ]]; then
  echo "  Additionally tagging as 'latest'."
fi

# Build the Docker image with the version tag
docker build \
  --build-arg NGS_PORTAL_VERSION="$NGS_PORTAL_COMMIT_SHA" \
  -t "$NGS_PORTAL_IMAGE:$NGS_PORTAL_VERSION" \
  -f sites/ngs-portal/Dockerfile .

# Exit immediately if the docker build fails
if [[ $? -ne 0 ]]; then
  echo "Docker build failed. Exiting."
  exit 1
fi

# Tag the image as "latest" if required
if [[ "$LATEST_TAG" == true ]]; then
  docker tag "$NGS_PORTAL_IMAGE:$NGS_PORTAL_VERSION" "$NGS_PORTAL_IMAGE:latest"
fi

# Push the images if --push is provided
if [[ "$PUSH_IMAGES" == true ]]; then
  echo "Pushing Docker images to the registry..."
  docker push "$NGS_PORTAL_IMAGE:$NGS_PORTAL_VERSION"
  if [[ "$LATEST_TAG" == true ]]; then
    docker push "$NGS_PORTAL_IMAGE:latest"
  fi
fi
