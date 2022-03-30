package com.epam.pipeline.manager.quota;

import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BillingQuotasScheduler extends AbstractSchedulingManager {

    private final BillingQuotasMonitor monitor;

    @PostConstruct
    public void setup() {
        scheduleFixedDelaySecured(monitor::checkQuotas,
                SystemPreferences.BILLING_QUOTAS_MONITORING_PERIOD_SECONDS,
                TimeUnit.SECONDS,
                "Billing quota check");
    }
}
