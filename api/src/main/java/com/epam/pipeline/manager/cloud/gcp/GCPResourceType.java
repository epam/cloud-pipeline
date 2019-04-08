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

package com.epam.pipeline.manager.cloud.gcp;

import org.apache.commons.lang3.StringUtils;

enum GCPResourceType {
    CPU, RAM, GPU;

    String alias() {
        return name().toLowerCase();
    }

    boolean isRequiredFor(final GCPMachine machine) {
        switch (this) {
            case CPU:
                return machine.getCpu() > 0;
            case RAM:
                return machine.getRam() > 0;
            case GPU:
                return machine.getGpu() > 0 && StringUtils.isNotBlank(machine.getGpuType());
        }
        throw new UnsupportedOperationException(String.format("Unsupported GCP resource type: %s.", this));
    }

    GCPResourceRequest requestFor(final GCPBilling billing, final GCPMachine machine, final String prefix) {
        switch (this) {
            case CPU:
            case RAM:
                return new GCPResourceRequest(machine.getFamily(), this, billing, prefix);
            case GPU:
                return new GCPResourceRequest(machine.getGpuType(), this, billing, prefix);
        }
        throw new UnsupportedOperationException(String.format("Unsupported GCP resource type: %s.", this));
    }

    String billingKeyFor(final GCPBilling billing, final GCPMachine machine) {
        switch (this) {
            case CPU:
            case RAM:
                return String.format("%s_%s_%s", alias(), billing.alias(), machine.getFamily());
            case GPU:
                return String.format("%s_%s_%s", alias(), billing.alias(), machine.getGpuType().toLowerCase());
        }
        throw new UnsupportedOperationException(String.format("Unsupported GCP resource type: %s.", this));
    }

    String familyFor(final GCPMachine machine) {
        switch (this) {
            case CPU:
            case RAM:
                return machine.getFamily();
            case GPU:
                return machine.getGpuType();
        }
        throw new UnsupportedOperationException(String.format("Unsupported GCP resource type: %s.", this));
    }

    public long priceFor(final GCPMachine machine, final GCPResourcePrice price) {
        switch (this) {
            case CPU:
                return machine.getCpu() * price.getNanos();
            case RAM:
                return Math.round(machine.getRam() * price.getNanos());
            case GPU:
                return machine.getGpu() * price.getNanos();
        }
        return 0;
    }
}
