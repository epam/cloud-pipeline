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

import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPDisk;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPObject;
import org.apache.commons.lang3.StringUtils;

public enum GCPResourceType {
    CPU {
        @Override
        public boolean isRequired(final GCPObject object) {
            return object instanceof GCPMachine && ((GCPMachine) object).getCpu() > 0;
        }

        @Override
        public String billingKey(final GCPBilling billing, final GCPObject object) {
            return String.format(BILLING_KEY_PATTERN, alias(), billing.alias(), object.getFamily());
        }

        @Override
        public String family(final GCPObject object) {
            return object.getFamily();
        }

        @Override
        public long price(final GCPObject object, final GCPResourcePrice price) {
            return ((GCPMachine) object).getCpu() * price.getNanos();
        }
    },

    RAM {
        @Override
        public boolean isRequired(final GCPObject object) {
            return object instanceof GCPMachine && ((GCPMachine) object).getRam() > 0;
        }

        @Override
        public String billingKey(final GCPBilling billing, final GCPObject object) {
            return String.format(BILLING_KEY_PATTERN, alias(), billing.alias(), object.getFamily());
        }

        @Override
        public String family(final GCPObject object) {
            return object.getFamily();
        }

        @Override
        public long price(final GCPObject object, final GCPResourcePrice price) {
            return Math.round(((GCPMachine) object).getRam() * price.getNanos());
        }
    },

    GPU {
        @Override
        public boolean isRequired(final GCPObject object) {
            return object instanceof GCPMachine
                    && ((GCPMachine) object).getGpu() > 0
                    && StringUtils.isNotBlank(((GCPMachine) object).getGpuType());
        }

        @Override
        public String billingKey(final GCPBilling billing, final GCPObject object) {
            return String.format(BILLING_KEY_PATTERN, alias(), billing.alias(), ((GCPMachine) object).getGpuType().toLowerCase());
        }

        @Override
        public String family(final GCPObject object) {
            return ((GCPMachine) object).getGpuType();
        }

        @Override
        public long price(final GCPObject object, final GCPResourcePrice price) {
            return ((GCPMachine) object).getGpu() * price.getNanos();
        }
    },

    DISK {
        @Override
        public boolean isRequired(final GCPObject object) {
            return object instanceof GCPDisk;
        }

        @Override
        public String billingKey(final GCPBilling billing, final GCPObject object) {
            return String.format(SHORT_BILLING_KEY_PATTERN, alias(), billing.alias());
        }

        @Override
        public String family(final GCPObject object) {
            return CloudInstancePriceService.STORAGE_PRODUCT_FAMILY;
        }

        @Override
        public long price(final GCPObject object, final GCPResourcePrice price) {
            return price.getNanos();
        }

        @Override
        public long normalize(final long nanos) {
            return nanos / HOURS_IN_MONTH;
        }
    };

    private static final String BILLING_KEY_PATTERN = "%s_%s_%s";
    private static final String SHORT_BILLING_KEY_PATTERN = "%s_%s";
    private static final long HOURS_IN_MONTH = 24 * 30;

    public String alias() {
        return name().toLowerCase();
    }

    public abstract boolean isRequired(GCPObject machine);

    public abstract String billingKey(GCPBilling billing, GCPObject machine);

    public abstract String family(GCPObject machine);

    public abstract long price(GCPObject machine, GCPResourcePrice price);

    public long normalize(final long nanos) {
        return nanos;
    }
}
