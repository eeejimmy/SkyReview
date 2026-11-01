-- Compare the thread identifier with the identifier stored in the lock
if(redis.call('get', KEYS[1]) ==  ARGV[1]) then
    -- Release the lock: del key
    return redis.call('del', KEYS[1])
end
return 0