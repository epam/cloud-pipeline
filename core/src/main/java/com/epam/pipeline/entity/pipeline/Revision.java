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

package com.epam.pipeline.entity.pipeline;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Revision {

    private Long id;
    private String name;
    private String message;
    private Date createdDate;
    private Boolean draft;
    private String commitId;
    private String author;
    private String authorEmail;


    public Revision() {
        this.id = 1L;
        this.draft = Boolean.FALSE;
    }

    public Revision(String name, String message, Date createdDate, String commitId, String author, String authorEmail) {
        this();
        this.name = name;
        this.message = message;
        this.createdDate = new Date(createdDate.getTime());
        this.commitId = commitId;
        this.author = author;
        this.authorEmail = authorEmail;
    }

    public Revision(String name, String message, Date createdDate, String commitId, Boolean draft,
                    String author, String authorEmail) {
        this(name, message, createdDate, commitId, author, authorEmail);
        this.draft = draft;
    }
}
