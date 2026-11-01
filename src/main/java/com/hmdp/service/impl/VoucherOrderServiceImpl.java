package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * Service implementation
 * </p>
 *
 * @author Hu Ge
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.Read orders from the stream: XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.Check whether the order list is empty
                    if (list == null || list.isEmpty()) {
                        // No messages; continue to the next iteration
                        continue;
                    }
                    // Parse the data
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.Create the order
                    createVoucherOrder(voucherOrder);
                    // 4.Acknowledge the message: XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.Read pending orders: XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.Check whether the order list is empty
                    if (list == null || list.isEmpty()) {
                        // No pending messages; exit the loop
                        break;
                    }
                    // Parse the data
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.Create the order
                    createVoucherOrder(voucherOrder);
                    // 4.Acknowledge the message: XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // 1.Read order information from the queue
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.Create the order
                    createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("Failed to process order", e);
                }
            }
        }
    }*/

    private void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // Create the lock object
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // Try to acquire the lock
        boolean isLock = redisLock.tryLock();
        // Check the result
        if (!isLock) {
            // Lock acquisition failed; return failure or retry
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.Query orders
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.Check whether it exists
            if (count > 0) {
                // The user has already purchased this voucher
                log.error("不允许重复下单！");
                return;
            }

            // 6.Deduct stock
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // Stock deduction failed
                log.error("库存不足！");
                return;
            }

            // 7.Create the order
            save(voucherOrder);
        } finally {
            // Release the lock
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.Execute the Lua script
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.Check whether the result is zero
        if (r != 0) {
            // 2.1.A nonzero result means the user is not eligible to purchase
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.Return the order ID
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.Execute the Lua script
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.Check whether the result is zero
        if (r != 0) {
            // 2.1.A nonzero result means the user is not eligible to purchase
            return Result.fail(r == 1 ? "Insufficient stock" : "Duplicate orders are not allowed");
        }
        // 2.2.A zero result means the user is eligible; add the order to the blocking queue
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.Order ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.User ID
        voucherOrder.setUserId(userId);
        // 2.5.Voucher ID
        voucherOrder.setVoucherId(voucherId);
        // 2.6.Add to the blocking queue
        orderTasks.add(voucherOrder);

        // 3.Return the order ID
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.Query the voucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.Check whether the flash sale has started
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // Not started yet
            return Result.fail("The flash sale has not started!");
        }
        // 3.Check whether the flash sale has ended
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // Not started yet
            return Result.fail("The flash sale has ended!");
        }
        // 4.Check whether sufficient stock is available
        if (voucher.getStock() < 1) {
            // Insufficient stock
            return Result.fail("Insufficient stock!");
        }

        return createVoucherOrder(voucherId);
    }



    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.One order per user
        Long userId = UserHolder.getUser().getId();

        // Create the lock object
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // Try to acquire the lock
        boolean isLock = redisLock.tryLock();
        // Check the result
        if(!isLock){
            // Lock acquisition failed; return failure or retry
            return Result.fail("Duplicate orders are not allowed!");
        }

        try {
            // 5.1.Query orders
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.Check whether it exists
            if (count > 0) {
                // The user has already purchased this voucher
                return Result.fail("The user has already purchased this voucher!");
            }

            // 6.Deduct stock
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // Stock deduction failed
                return Result.fail("Insufficient stock!");
            }

            // 7.Create the order
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.Order ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.User ID
            voucherOrder.setUserId(userId);
            // 7.3.Voucher ID
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.Return the order ID
            return Result.ok(orderId);
        } finally {
            // Release the lock
            redisLock.unlock();
        }

    }*/
    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.One order per user
        Long userId = UserHolder.getUser().getId();

        // Create the lock object
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // Try to acquire the lock
        boolean isLock = redisLock.tryLock(1200);
        // Check the result
        if(!isLock){
            // Lock acquisition failed; return failure or retry
            return Result.fail("Duplicate orders are not allowed!");
        }

        try {
            // 5.1.Query orders
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.Check whether it exists
            if (count > 0) {
                // The user has already purchased this voucher
                return Result.fail("The user has already purchased this voucher!");
            }

            // 6.Deduct stock
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // Stock deduction failed
                return Result.fail("Insufficient stock!");
            }

            // 7.Create the order
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.Order ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.User ID
            voucherOrder.setUserId(userId);
            // 7.3.Voucher ID
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.Return the order ID
            return Result.ok(orderId);
        } finally {
            // Release the lock
            redisLock.unlock();
        }

    }*/

    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.One order per user
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 5.1.Query orders
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.Check whether it exists
            if (count > 0) {
                // The user has already purchased this voucher
                return Result.fail("The user has already purchased this voucher!");
            }

            // 6.Deduct stock
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // Stock deduction failed
                return Result.fail("Insufficient stock!");
            }

            // 7.Create the order
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.Order ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.User ID
            voucherOrder.setUserId(userId);
            // 7.3.Voucher ID
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 7.Return the order ID
            return Result.ok(orderId);
        }
    }*/
}
