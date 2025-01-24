# +-----------------------------------------------------------------+
# !!! IMPORTANT !!!
#
#     ngs-portal docker image should be built from
#     portals-ui directory (lerna root), i.e.
#
# ```sh
# cd <...>/cloud-pipeline/portals-ui
# docker build -t ngs-portal:latest -f sites/ngs-portal/Dockerfile .
# ```
#
# +------------------------------------------------------------------+

FROM library/ubuntu:20.04 AS build-stage
ENV DEBIAN_FRONTEND noninteractive
# Set the working directory
WORKDIR /app
# Install necessary dependencies
RUN apt-get update && apt-get install -y \
    curl \
    build-essential \
    python3 \
    apt-utils \
    && rm -rf /var/lib/apt/lists/*
# Add NodeSource repository and install Node.js v22
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Copy package files and install dependencies
COPY lerna.json ./
COPY nx.json ./
COPY package*.json ./
COPY ./packages ./packages
COPY ./sites ./sites

RUN npm i
RUN npm run ngs-portal-build

FROM library/ubuntu:20.04 as main-stage
# Set working directory
WORKDIR /opt/ngs-portal

# Copy built application from the build stage
COPY --from=build-stage /app/sites/ngs-portal/dist /opt/ngs-portal

# Expose the necessary port
EXPOSE 8080
