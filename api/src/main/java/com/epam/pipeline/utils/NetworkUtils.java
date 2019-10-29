package com.epam.pipeline.utils;

import com.epam.pipeline.entity.region.AzurePolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.InetAddressValidator;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class NetworkUtils {

    private static final int SHIFT_MASK = 0xFF;

    private NetworkUtils() {}

    public static boolean isIpRangeValid(final AzurePolicy policy) {
        if (!(StringUtils.isNotBlank(policy.getIpMax()) && StringUtils.isNotBlank(policy.getIpMin()))) {
            // is not a range
            return true;
        }

        try {
            final byte[] maxIp = InetAddress.getByName(policy.getIpMax()).getAddress();
            final byte[] minIp = InetAddress.getByName(policy.getIpMin()).getAddress();

            if (maxIp.length < minIp.length) {
                return false;
            }

            if (maxIp.length > minIp.length) {
                return true;
            }

            for (int i = 0; i < maxIp.length; i++) {
                final int b1 = unsignedByteToInt(maxIp[i]);
                final int b2 = unsignedByteToInt(minIp[i]);
                if (b1 != b2) {
                    return b1 > b2;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    String.format("Azure policy IP range is invalid: min - %s, max - %s.",
                            policy.getIpMax(), policy.getIpMin()),
                    e
            );
        }
    }

    public static boolean isValidIpAddress(final String ip) {
        return StringUtils.isBlank(ip) || InetAddressValidator.getInstance().isValid(ip);
    }

    private static int unsignedByteToInt(final byte b) {
        return (int) b & SHIFT_MASK;
    }

}
