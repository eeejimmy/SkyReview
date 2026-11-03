package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherOrderPersistenceService {
    private final VoucherOrderMapper orders;
    private final SeckillVoucherMapper vouchers;
    private final OrderFailureInjector failureInjector;

    public VoucherOrderPersistenceService(VoucherOrderMapper orders,
            SeckillVoucherMapper vouchers, OrderFailureInjector failureInjector) {
        this.orders = orders;
        this.vouchers = vouchers;
        this.failureInjector = failureInjector;
    }

    @Transactional(rollbackFor = Exception.class)
    public void persist(VoucherOrder order) {
        VoucherOrder existing = orders.selectById(order.getId());
        if (existing != null) {
            if (!existing.getUserId().equals(order.getUserId())
                    || !existing.getVoucherId().equals(order.getVoucherId())) {
                throw new IllegalStateException("Order ID belongs to another purchase");
            }
            return; // Re-delivery after commit but before ACK.
        }
        if (orders.selectCount(new QueryWrapper<VoucherOrder>()
                .eq("user_id", order.getUserId())
                .eq("voucher_id", order.getVoucherId())) > 0) {
            return; // Already purchased: do not deduct again.
        }
        int changed = vouchers.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId()).gt("stock", 0));
        if (changed != 1) {
            throw new IllegalStateException("Database stock unavailable; message remains pending");
        }
        failureInjector.afterStockDeducted(order);
        if (orders.insert(order) != 1) {
            throw new IllegalStateException("Order insert failed");
        }
    }
}
