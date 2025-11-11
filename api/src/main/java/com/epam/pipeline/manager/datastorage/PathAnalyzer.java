package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.run.parameter.DataStorageLink;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.utils.DataStorageUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PathAnalyzer {

    private final DataStorageDao dataStorageDao;

    public void analyzePaths(List<PipeConfValue> values) {
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        List<AbstractDataStorage> dataStorages = dataStorageDao.loadAllDataStorages();
        values.forEach(value -> {
            List<DataStorageLink> links = new ArrayList<>();
            for (AbstractDataStorage dataStorage : dataStorages) {
                List<DataStorageLink> dataStorageLinks = getLinks(dataStorage, value.getValue());
                if (!dataStorageLinks.isEmpty()) {
                    links.addAll(dataStorageLinks);
                }
            }
            if (!links.isEmpty()) {
                value.setDataStorageLinks(links);
            }
        });
    }

    public static List<DataStorageLink> getLinks(AbstractDataStorage dataStorage, String paramValue) {
        if (StringUtils.isBlank(paramValue)) {
            return Collections.emptyList();
        }
        final String mask = dataStorage.getPathMask() + ProviderUtils.DELIMITER;
        List<DataStorageLink> links = new ArrayList<>();
        String paramDelimiter = paramValue.contains(",") ? "," : ";";
        for (String path : paramValue.split(paramDelimiter)) {
            if (path.toLowerCase().trim().startsWith(mask.toLowerCase())) {
                links.add(DataStorageUtils.constructDataStorageLink(dataStorage, path, mask));
            }
        }
        return links;
    }
}
