package com.epam.pipeline.controller.vo.data.storage;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RestoreFolderVO {
    private boolean recursively;
    private List<String> contentFilter;




}
