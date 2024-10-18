/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function filterPods (pods = [], filters = {}) {
  const byGlobalSearch = entity => {
    if (!filters.globalSearch) {
      return true;
    }
    if (entity.isContainer) {
      return [entity.name]
        .some(field => (field || '')
          .toLowerCase()
          .includes(filters.globalSearch.toLowerCase()));
    }
    return [entity.name, entity.namespace, entity.nodeName, entity.phase]
      .some(field => (field || '')
        .toLowerCase()
        .includes(filters.globalSearch.toLowerCase()));
  };
  const byStatus = pod => filters.status
    ? pod.phase?.toLowerCase() === filters.status.toLowerCase()
    : true;
  const byNodeName = pod => filters.nodeName
    ? pod.nodeName === filters.nodeName
    : true;
  const byName = pod => {
    const podEligible = pod.name?.toLowerCase().includes(filters.name.toLowerCase());
    const containersEligible = (pod.containers || [])
      .some(container => container.name?.toLowerCase()
        .includes(filters.name.toLowerCase())
      );
    return filters.name
      ? podEligible || containersEligible
      : true;
  };
  const byNamespace = pod => filters.namespace
    ? pod.namespace?.toLowerCase() === filters.namespace.toLowerCase()
    : true;
  const podMatchers = [
    byNodeName,
    byStatus,
    byName,
    byNamespace
  ];
  return pods
    .filter(pod => podMatchers.every(matchFn => matchFn(pod)))
    .filter(pod => {
      const podEligible = byGlobalSearch(pod);
      const podContainersEligible = pod.containers
        .some(container => filters.globalSearch && byGlobalSearch(container));
      return podEligible || podContainersEligible;
    });
}

function extractDataset (pods = []) {
  const services = pods.reduce((acc, pod) => {
    if (!acc[pod.parentName]) {
      acc[pod.parentName] = [];
    }
    acc[pod.parentName].push(pod);
    return acc;
  }, {});
  return Object.entries(services).map(([key, service]) => ({
    name: key,
    uid: key,
    isService: true,
    children: service.map(pod => ({
      nodeName: pod.nodeName,
      name: pod.name,
      namespace: pod.namespace,
      parentType: pod.parentType,
      status: pod.phase,
      uid: pod.uid,
      uptime: pod.status.timestamp,
      restarts: pod.containers
        .reduce((acc, container) => (container.restartCount || 0) + acc, 0),
      children: pod.containers.map((container, index) => ({
        name: container.name,
        isContainer: true,
        uid: `${pod.uid}-${index}`,
        status: container.status,
        restarts: container.restartCount,
        podName: pod.name
      }))
    }))
  }))
    .map(service => ({
      ...service,
      status: {
        healthy: service.children
          .filter(pod => pod.status.toUpperCase() === 'RUNNING' ||
            pod.status.toUpperCase() === 'SUCCEEDED').length,
        unhealthy: service.children
          .filter(pod => pod.status.toUpperCase() === 'FAILED').length,
        pending: service.children
          .filter(pod => pod.status.toUpperCase() === 'PENDING').length
      }
    }));
}

function formatPodDescriptionString (description = '{}') {
  try {
    const descriptionObject = JSON.parse(description);
    return [JSON.stringify(descriptionObject, null, 2), null];
  } catch (error) {
    return ['{}', 'Failed to parse pod description'];
  }
}

function extractExpandableUids (filteredPods = []) {
  const uniquesIdsSet = filteredPods.reduce((acc, service) => {
    acc.add(service.uid);
    (service.children || []).forEach(pod => {
      acc.add(pod.uid);
    });
    return acc;
  }, new Set());
  return [...uniquesIdsSet].filter(Boolean);
}

export {
  extractDataset,
  extractExpandableUids,
  filterPods,
  formatPodDescriptionString
};
