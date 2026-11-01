package com.hmdp.utils;

public interface ILock {

    /**
     * Try to acquire the lock
     * @param timeoutSec Lock timeout in seconds; automatically released on expiry
     * @return true if the lock was acquired; false otherwise
     */
    boolean tryLock(long timeoutSec);

    /**
     * Release the lock
     */
    void unlock();
}
