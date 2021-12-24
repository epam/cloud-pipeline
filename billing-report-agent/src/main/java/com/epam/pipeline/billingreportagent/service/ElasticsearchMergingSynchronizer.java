package com.epam.pipeline.billingreportagent.service;

public interface ElasticsearchMergingSynchronizer extends ElasticsearchSynchronizer {

    ElasticsearchMergingFrame frame();
}
