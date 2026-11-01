package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // Set the logical expiration time
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // Write to Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.Query the shop cache in Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.Check whether it exists
        if (StrUtil.isNotBlank(json)) {
            // 3.Record found; return immediately
            return JSONUtil.toBean(json, type);
        }
        // Check whether the cached value is empty
        if (json != null) {
            // Return an error message
            return null;
        }

        // 4.Cache miss; query the database by ID
        R r = dbFallback.apply(id);
        // 5.Not found; return an error
        if (r == null) {
            // Cache an empty value in Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // Return an error message
            return null;
        }
        // 6.Record found; write it to Redis
        this.set(key, r, time, unit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.Query the shop cache in Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.Check whether it exists
        if (StrUtil.isBlank(json)) {
            // 3.Record found; return immediately
            return null;
        }
        // 4.Cache hit; deserialize the JSON into an object
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.Check whether the entry has expired
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.Not expired; return the shop information
            return r;
        }
        // 5.2.Entry expired; rebuild the cache
        // 6.Cache rebuild
        // 6.1.Acquire the mutex
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.Check whether the lock was acquired
        if (isLock){
            // 6.3.Lock acquired; rebuild the cache in a separate thread
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // Query the database
                    R newR = dbFallback.apply(id);
                    // Rebuild the cache
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // Release the lock
                    unlock(lockKey);
                }
            });
        }
        // 6.4.Return the expired shop information
        return r;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.Query the shop cache in Redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.Check whether it exists
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.Record found; return immediately
            return JSONUtil.toBean(shopJson, type);
        }
        // Check whether the cached value is empty
        if (shopJson != null) {
            // Return an error message
            return null;
        }

        // 4.Rebuild the cache
        // 4.1.Acquire the mutex
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.Check whether acquisition succeeded
            if (!isLock) {
                // 4.3.Lock acquisition failed; sleep and retry
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.Lock acquired; query the database by ID
            r = dbFallback.apply(id);
            // 5.Not found; return an error
            if (r == null) {
                // Cache an empty value in Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // Return an error message
                return null;
            }
            // 6.Record found; write it to Redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7.Release the lock
            unlock(lockKey);
        }
        // 8.Return
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
