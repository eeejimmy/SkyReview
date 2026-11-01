-- 1.Parameter list
-- 1.1.Voucher ID
local voucherId = ARGV[1]
-- 1.2.User ID
local userId = ARGV[2]
-- 1.3.Order ID
local orderId = ARGV[3]

-- 2.Data keys
-- 2.1.Stock key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.Order key
local orderKey = 'seckill:order:' .. voucherId

-- 3.Script logic
-- 3.1.Check stock availability: get stockKey
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2.Insufficient stock; return 1
    return 1
end
-- 3.2.Check whether the user has ordered: SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.User already recorded; return 2 for a duplicate order
    return 2
end
-- 3.4.Deduct stock: incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.Record the purchasing user: sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.6.Send a message to the stream: XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0