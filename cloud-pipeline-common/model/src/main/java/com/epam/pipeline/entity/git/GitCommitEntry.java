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

package com.epam.pipeline.entity.git;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class GitCommitEntry {

    private String id;
    @JsonProperty("short_id")
    private String shortId;
    private String title;
    @JsonProperty("author_name")
    private String authorName;
    @JsonProperty("author_email")
    private String authorEmail;
    @JsonProperty("authored_date")
    private String authoredDate;
    @JsonProperty("committer_name")
    private String committerName;
    @JsonProperty("committer_email")
    private String committerEmail;
    @JsonProperty("committed_date")
    private String committedDate;
    @JsonProperty("created_at")
    private String createdAt;
    private String message;
    @JsonProperty("parend_ids")
    private List<String> parentIds;

    private String getCommittedDateString() {
        String committedDateStr = null;
        if (this.committedDate != null && !this.committedDate.isEmpty()) {
            committedDateStr = this.committedDate;
        } else if (this.createdAt != null && !this.createdAt.isEmpty()) {
            committedDateStr = this.createdAt;
        }
        if (committedDateStr != null) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            try {
                Date date = parser.parse(committedDateStr);
                return date.toString();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Override
    public String toString() {
        String committedDateStr = this.getCommittedDateString();

        return String.format("%s %s: %s",
                committedDateStr,
                this.committerName,
                this.title);
    }

}
