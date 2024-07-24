/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.eventsourcing.acl;

import com.epam.pipeline.eventsourcing.Event;
import com.epam.pipeline.eventsourcing.EventProducer;
import com.epam.pipeline.eventsourcing.EventType;

import java.util.concurrent.atomic.AtomicReference;

public class ACLUpdateEventProducer implements EventProducer {

    private final AtomicReference<EventProducer> inner;

    public ACLUpdateEventProducer() {
        inner = new AtomicReference<>();
    }

     public void init(EventProducer producer) {
        this.inner.set(producer);
     }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    public String getEventType() {
        return EventType.ACL.name();
    }

    @Override
    public long put(Event event) {
      if (inner.get() != null) {
         return inner.get().put(event);
      }
      return -1;
    }
}
