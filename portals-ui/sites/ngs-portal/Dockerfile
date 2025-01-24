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

FROM node:22 AS build-stage
# Set the working directory
WORKDIR /app

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
