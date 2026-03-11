package dfm.medischeduler_routeservice;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Distributed lock backed by Redisson to prevent concurrent uploads
 * for the same client.
 *
 * When a client initiates a scheduling run, the {@link MatchController}
 * acquires a lock keyed by the client ID. This ensures that only one
 * upload is processed at a time per client, preventing data corruption
 * in Redis.
 */
@Component
public class RedisLock {

    private final RedissonClient redisson;

    public RedisLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * Attempts to acquire a distributed lock for the given client.
     *
     * @param clientId the client identifier used as the lock key
     * @param waitMs   maximum time in milliseconds to wait for the lock
     * @param leaseMs  time in milliseconds before the lock auto-expires
     * @return {@code true} if the lock was acquired, {@code false} otherwise
     */
    public boolean acquireLock(String clientId, long waitMs, long leaseMs) throws InterruptedException {
        RLock lock = redisson.getLock("uploadLock:" + clientId);
        Thread.interrupted();
        return lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Releases the distributed lock for the given client, but only if
     * the current thread holds it.
     *
     * @param clientId the client identifier used as the lock key
     */
    public void releaseLock(String clientId) {
        RLock lock = redisson.getLock("uploadLock:" + clientId);
        if (lock.isLocked()) {
            lock.forceUnlock();
        }
    }
}
