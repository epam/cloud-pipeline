package com.epam.pipeline.manager.cloud.offer;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class InstanceOfferUniqueFilter implements InstanceOfferFilter {

    @Override
    public List<InstanceOffer> filter(final List<InstanceOffer> offers) {
        log.debug("Filtering unique instance offers...");
        final List<InstanceOffer> filteredOffers = offers.stream()
                .map(InstanceOfferComparable::wrap)
                .distinct()
                .map(InstanceOfferComparable::unwrap)
                .collect(Collectors.toList());
        log.debug("Filtered out {} instance offers.", offers.size() - filteredOffers.size());
        return filteredOffers;
    }

    @RequiredArgsConstructor
    public static class InstanceOfferComparable {

        private final InstanceOffer offer;

        public static InstanceOfferComparable wrap(final InstanceOffer offer) {
            return new InstanceOfferComparable(offer);
        }

        public InstanceOffer unwrap() {
            return offer;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final InstanceOfferComparable other = (InstanceOfferComparable) o;
            return equalsOffer(this.offer, other.offer);
        }

        public boolean equalsOffer(final InstanceOffer left, final InstanceOffer right) {
            return Objects.equals(left.getTermType(), right.getTermType())
                    && Objects.equals(left.getUnit(), right.getUnit())
                    && Double.compare(left.getPricePerUnit(), right.getPricePerUnit()) == 0
                    && Objects.equals(left.getCurrency(), right.getCurrency())
                    && Objects.equals(left.getInstanceType(), right.getInstanceType())
                    && Objects.equals(left.getTenancy(), right.getTenancy())
                    && Objects.equals(left.getOperatingSystem(), right.getOperatingSystem())
                    && Objects.equals(left.getProductFamily(), right.getProductFamily())
                    && Objects.equals(left.getVolumeType(), right.getVolumeType())
                    && Objects.equals(left.getVolumeApiName(), right.getVolumeApiName())
                    && Objects.equals(left.getPriceListPublishDate(), right.getPriceListPublishDate())
                    && left.getVCPU() == right.getVCPU()
                    && Double.compare(left.getMemory(), right.getMemory()) == 0
                    && Objects.equals(left.getMemoryUnit(), right.getMemoryUnit())
                    && Objects.equals(left.getInstanceFamily(), right.getInstanceFamily())
                    && left.getGpu() == right.getGpu()
                    && Objects.equals(left.getGpuDevice(), right.getGpuDevice())
                    && Objects.equals(left.getRegionId(), right.getRegionId())
                    && left.getCloudProvider() == right.getCloudProvider();
        }

        @Override
        public int hashCode() {
            return hashCodeOffer(offer);
        }

        private int hashCodeOffer(final InstanceOffer offer) {
            return Objects.hash(offer.getTermType(), offer.getUnit(), offer.getPricePerUnit(), offer.getCurrency(),
                    offer.getInstanceType(), offer.getTenancy(), offer.getOperatingSystem(), offer.getProductFamily(),
                    offer.getVolumeType(), offer.getVolumeApiName(), offer.getPriceListPublishDate(), offer.getVCPU(),
                    offer.getMemory(), offer.getMemoryUnit(), offer.getInstanceFamily(), offer.getGpu(),
                    offer.getGpuDevice(), offer.getRegionId(), offer.getCloudProvider());
        }
    }
}
