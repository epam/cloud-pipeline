package com.epam.pipeline.manager.dts;

import com.epam.pipeline.dao.dts.DtsRegistryDao;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DtsTunnelResolver {

    private static final String DTS_TUNNEL_ENABLED_KEY = "dts.tunnel.enabled";
    private static final String DTS_TUNNEL_HOST_KEY = "dts.tunnel.host";
    private static final String DTS_TUNNEL_INPUT_PORT_KEY = "dts.tunnel.input.port";
    private static final String DTS_TUNNEL_OUTPUT_PORT_KEY = "dts.tunnel.output.port";
    private static final String FALLBACK_DTS_TUNNEL_HOSTS = "cp-api-srv.default.svc.cluster.local";
    private static final String FALLBACK_DTS_TUNNEL_INPUT_PORTS = "5000-5009";
    private static final String FALLBACK_DTS_TUNNEL_OUTPUT_PORTS = "5010-5019";

    private final DtsRegistryDao dtsRegistryDao;
    private final PreferenceManager preferenceManager;

    public Optional<DtsTunnelAddress> resolve() {
        return resolveAvailableAddress(getTunnelHosts(), getInputPorts(), getOutputPorts(),
                collectUnavailableAddresses());
    }

    private String getTunnelHosts() {
        return getSystemPreference(SystemPreferences.DTS_TUNNEL_HOSTS).orElse(FALLBACK_DTS_TUNNEL_HOSTS);
    }

    private String getInputPorts() {
        return getSystemPreference(SystemPreferences.DTS_TUNNEL_INPUT_PORTS).orElse(FALLBACK_DTS_TUNNEL_INPUT_PORTS);
    }

    private String getOutputPorts() {
        return getSystemPreference(SystemPreferences.DTS_TUNNEL_OUTPUT_PORTS).orElse(FALLBACK_DTS_TUNNEL_OUTPUT_PORTS);
    }

    private <T> Optional<T> getSystemPreference(final AbstractSystemPreference<T> preference) {
        return Optional.of(preference).map(preferenceManager::getPreference);
    }

    private List<DtsTunnelAddress> collectUnavailableAddresses() {
        return dtsRegistryDao.loadAll().stream()
                .filter(this::isTunnelEnabled)
                .map(this::toTunnelAddress)
                .collect(Collectors.toList());
    }

    private boolean isTunnelEnabled(final DtsRegistry dts) {
        final Map<String, String> preferences = MapUtils.emptyIfNull(dts.getPreferences());
        return getBooleanPreference(preferences, DTS_TUNNEL_ENABLED_KEY);
    }

    private DtsTunnelAddress toTunnelAddress(final DtsRegistry dts) {
        final Map<String, String> preferences = MapUtils.emptyIfNull(dts.getPreferences());
        return new DtsTunnelAddress(getStringPreference(preferences, DTS_TUNNEL_HOST_KEY),
                getIntegerPreference(preferences, DTS_TUNNEL_INPUT_PORT_KEY),
                getIntegerPreference(preferences, DTS_TUNNEL_OUTPUT_PORT_KEY));
    }

    private String getStringPreference(final Map<String, String> preferences, final String preference) {
        return getPreference(preferences, preference).orElse(null);
    }

    private Integer getIntegerPreference(final Map<String, String> preferences, final String preference) {
        return getPreference(preferences, preference).map(NumberUtils::toInt).orElse(null);
    }

    private boolean getBooleanPreference(final Map<String, String> preferences, final String preference) {
        return getPreference(preferences, preference).map(BooleanUtils::toBoolean).orElse(false);
    }

    private Optional<String> getPreference(final Map<String, String> preferences, final String preference) {
        return Optional.of(preference).map(preferences::get);
    }

    private Optional<DtsTunnelAddress> resolveAvailableAddress(final String hosts,
                                                               final String inputPorts,
                                                               final String outputPorts,
                                                               final List<DtsTunnelAddress> unavailableAddresses) {

        return Optional.of(hosts)
                .map(host -> StringUtils.split(host, ","))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .flatMap(host -> portPairs(inputPorts, outputPorts)
                        .map(ports -> new DtsTunnelAddress(host, ports.getLeft(), ports.getRight())))
                .filter(address -> !unavailableAddresses.contains(address))
                .findFirst();
    }

    private Stream<Pair<Integer, Integer>> portPairs(final String leftPorts, final String rightPorts) {
        return StreamUtils.zipped(ports(leftPorts), ports(rightPorts));
    }

    private Stream<Integer> ports(final String ports) {
        return Optional.of(ports)
                .map(this::getRangeItems)
                .orElseGet(Stream::empty);
    }

    private Stream<Integer> getRangeItems(final String range) {
        final List<Integer> bounds = getRangeBounds(range);
        return bounds.size() == 2 ? IntStream.range(bounds.get(0), bounds.get(1) + 1).boxed()
                : bounds.size() == 1 ? Stream.of(bounds.get(0))
                : Stream.empty();
    }

    private List<Integer> getRangeBounds(final String range) {
        return Arrays.stream(range.split("-"))
                .map(NumberUtils::toInt)
                .collect(Collectors.toList());
    }

}
