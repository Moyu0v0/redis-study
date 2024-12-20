-- 1. 参数列表
-- 1.1 秒杀券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]

-- 2. 数据key
-- 2.1 秒杀券库存key 用..来拼接字符串
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 秒杀券订单key
local orderKey = 'seckill:order:' .. userId

-- 3. 脚本业务
-- FIXME 缺少判断是否过期的逻辑
-- 3.1 判断库存是否充足 GET stockKey
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end
-- 3.2 判断用户是否下单 SISMEMBER orderKey userId
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 重复下单，返回2
    return 2
end
-- 3.3 扣减库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.4 下单（保存用户） sadd orderKey userId
redis.call('sadd', orderKey, userId)
return 0