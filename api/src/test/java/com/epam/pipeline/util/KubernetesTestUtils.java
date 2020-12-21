/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.util;

import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.Getter;
import java.util.Map;

/**
 * A test utils class, that simplifies mocking Kubernetes response entities
 */
public class KubernetesTestUtils {
    public interface MockParentEntity<ME> {
        ME getMockedEntity();
    }

    /**
     * Constructs mock object for results of KubernetesClient::nodes() method.
     * A typical usage could be the following:
     * <pre>
     *     NonNamespaceOperation&lt;Node, NodeList, DoneableNode, Resource&lt;Node, DoneableNode>> mockNodes =
     *         new KubernetesTestUtils.MockNodes()
     *             .mockWithLabel(KubernetesConstants.RUN_ID_LABEL)
     *                 .mockWithoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
     *                 .mockNodeList(Collections.emptyList())
     *             .and()
     *             .getMockedEntity();
     *
     *     when(kubernetesClient.nodes()).thenReturn(mockNodes);
     * </pre>
     */
    public static class MockNodes implements MockParentEntity<NonNamespaceOperation> {
        private NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> nodes =
            mock(NonNamespaceOperation.class);

        public MockFilter<MockNodes, Node, NodeList> mockWithLabel(String label) {
            return mockWithLabel(Matchers.is(label));
        }

        public MockFilter<MockNodes, Node, NodeList> mockWithLabel(Matcher<String> matcher) {
            MockFilter<MockNodes, Node, NodeList> mockFilter = new MockFilter<>(this);
            when(nodes.withLabel(Mockito.argThat(matcher))).thenReturn(mockFilter.getFilter());
            return mockFilter;
        }

        public MockFilter<MockNodes, Node, NodeList> mockWithLabels(Map<String, String> matcher) {
            MockFilter<MockNodes, Node, NodeList> mockFilter = new MockFilter<>(this);
            when(nodes.withLabels(anyMap())).thenReturn(mockFilter.getFilter());
            return mockFilter;
        }

        @Override
        public NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> getMockedEntity() {
            return nodes;
        }
    }

    /**
     * Constructs mock object for results of KubernetesClient::pods() method.
     * A typical usage could be the following:
     * <pre>
     *     MixedOperation&lt;Pod, PodList, DoneablePod, PodResource&lt;Pod, DoneablePod>> mockPods =
     *         new KubernetesTestUtils.MockPods()
     *             .mockNamespace(Matchers.any(String.class))
     *                 .mockWithName(Matchers.any(String.class))
     *                 .mockPod(mockPod)
     *             .and()
     *             .getMockedEntity();
     *
     *     Mockito.when(mockClient.pods()).thenReturn(mockPods);
     * </pre>
     */
    public static class MockPods implements MockParentEntity<MixedOperation> {
        private MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> pods =
            (MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>>) mock(MixedOperation.class);

        public MockNamespace mockNamespace(String namespace) {
            return mockNamespace(Matchers.equalTo(namespace));
        }

        public MockNamespace mockNamespace(Matcher<String> matcher) {
            MockNamespace mockNamespace = new MockNamespace(this);
            when(pods.inNamespace(Mockito.argThat(matcher))).thenReturn(mockNamespace.getNamespace());
            return mockNamespace;
        }

        @Override
        public MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> getMockedEntity() {
            return pods;
        }
    }

    /**
     * Constructs results for various filter methods: 'withLabel(), withLabels, etc'
     * @param <T> the class of parent mock entity (e.g. MockNodes, MockPods)
     * @param <E> the class of content entity (e.g. Node, Pod)
     * @param <EL> the class of content list entity (e.g. NodeList, PodList)
     */
    public static class MockFilter<T extends MockParentEntity, E, EL> {
        private T parent;
        @Getter
        private FilterWatchListDeletable<E, EL, Boolean, Watch, Watcher<E>> filter;

        public MockFilter(T mockNodes) {
            this.parent = mockNodes;
            this.filter = mock(FilterWatchListDeletable.class);
        }

        public T and() {
            return parent;
        }

        public MockFilter<T, E, EL> mockWithoutLabel(String label) {
            return mockWithoutLabel(Matchers.is(label));
        }

        public MockFilter<T, E, EL> mockWithoutLabel(Matcher<String> matcher) {
            MockFilter<T, E, EL> mockFilter = new MockFilter<>(parent);
            when(filter.withoutLabel(Mockito.argThat(matcher))).thenReturn(mockFilter.getFilter());
            return mockFilter;
        }

        public MockFilter<T, E, EL> mockWithLabel(String label) {
            return mockWithLabel(Matchers.is(label));
        }

        public MockFilter<T, E, EL> mockWithLabel(Matcher<String> matcher) {
            MockFilter<T, E, EL> mockFilter = new MockFilter<>(parent);
            when(filter.withLabel(Mockito.argThat(matcher))).thenReturn(mockFilter.getFilter());
            return mockFilter;
        }

        public MockFilter<T, E, EL> mockNodeList(List<Node> nodes) {
            NodeList list = new NodeList("", nodes, "", null);
            when(filter.list()).thenReturn((EL) list);
            return this;
        }

        public MockFilter<T, E, EL> mockPodList(List<Pod> pods) {
            PodList list = new PodList("", pods, "", null);
            when(filter.list()).thenReturn((EL) list);
            return this;
        }
    }

    /**
     * Constructs mock for results of 'inNamespace()' method
     */
    public static class MockNamespace {
        private MockPods mockPods;

        @Getter
        private NonNamespaceOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> namespace;

        public MockNamespace(MockPods mockPods) {
            this.mockPods = mockPods;
            this.namespace = mock(NonNamespaceOperation.class);
        }

        public MockPods and() {
            return mockPods;
        }

        public MockFilter<MockPods, Pod, PodList> mockWithLabel(String label, String value) {
            MockFilter<MockPods, Pod, PodList> mockFilter = new MockFilter<>(mockPods);
            when(namespace.withLabel(Mockito.eq(label), Mockito.eq(value))).thenReturn(mockFilter.getFilter());
            return mockFilter;
        }

        public MockPodResource mockWithName(String name) {
            return mockWithName(Matchers.is(name));
        }

        public MockPodResource mockWithName(Matcher<String> matcher) {
            MockPodResource mockPodResource = new MockPodResource(mockPods);
            when(namespace.withName(Mockito.argThat(matcher))).thenReturn(mockPodResource.podResource);
            return mockPodResource;
        }
    }

    public static class MockPodResource {
        private MockPods mockPods;
        private PodResource<Pod, DoneablePod> podResource = mock(PodResource.class);

        public MockPodResource(MockPods mockPods) {
            this.mockPods = mockPods;
        }

        public MockPods and() {
            return mockPods;
        }

        public MockPodResource mockPod(Pod pod) {
            when(podResource.get()).thenReturn(pod);
            return this;
        }
    }
}
