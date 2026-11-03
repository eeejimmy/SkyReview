package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in local demonstration only. One failure per process, on the specified voucher. */
@Component
public class OrderFailureInjector {
    private final Environment environment;
    private final long voucherId;
    private final AtomicBoolean failed = new AtomicBoolean();

    public OrderFailureInjector(Environment environment,
            @Value("${hmdp.orders.fail-once-voucher-id:-1}") long voucherId) {
        this.environment = environment;
        this.voucherId = voucherId;
    }

    public void afterStockDeducted(VoucherOrder order) {
        if (environment.acceptsProfiles(Profiles.of("local"))
                && Long.valueOf(voucherId).equals(order.getVoucherId())
                && failed.compareAndSet(false, true)) {
            throw new IllegalStateException("INJECTED: after stock deduction, before order insert");
        }
    }
}
