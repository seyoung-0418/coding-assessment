## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```java
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMs = 60000; // 1 minute

    public static class CacheEntry<V> {
        private final V value;
        private final long timestamp;

        public CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public V getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
    }

    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() - entry.getTimestamp() < ttlMs) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int size() {
        return cache.size();
    }
}
```
## Review findings

- P0: Critical. Can cause a serious production issue, such as a service outage or data corruption. Must fix before deployment.
- P1: Major. Can cause incorrect data, performance issues or customer impact. Fix before deployment.
- P2: Minor. Low or no direct customer impact. Improve maintainability or operations.
- P3: Minor. Nice to have. No expected production impact.
- Question: Need to clarify the requirements before making a decision. The priority depends on the answer.

1. [P1] Maximum cache size (Line 10)
   I would add a maximum cache size.
   Even with TTL and cleanup, the cache can use too much memory if we keep adding new keys.
   This can cause more GC activity or OutOfMemoryError. I would use an eviction policy like LRU or LFU.

2. [P3] TTL configuration (Line 11)
   I would make TTL configurable through the constructor.
   If we need a different TTL for each entry, we can also put it in CacheEntry.
   I would validate the TTL and allow a time source to be passed in so we can test expiration without waiting for one minute.

3. [P3] TTL readability (Line 11)
   I would use `60_000` instead of `60000` for readability.

4. [Question, P1] Mutable values (Lines 14, 17–20, 22–24)
   Even though the reference is final, the object itself can still be changed.
   For example, if the value is an ArrayList, multiple threads can get the same list and modify it at the same time.
   This can cause a race condition and incorrect data.
   I would check if we need to support mutable values. I would recommend using immutable or thread-safe values.

5. [P2] Null keys or values (Lines 31–32, 35–36)
   I would clarify whether null keys and values are allowed. 
   ConcurrentHashMap does not allow null keys or null values, but CacheEntry can hold a null value.
   I would reject null values to avoid confusion between cached null, expired or missing data in get api.
   I would also handle null keys consistently in put and get api.

6. [P1] System clock changes (Lines 32, 38)
   System.currentTimeMillis method depends on the system clock.
   If the clock changes, an entry can expire too early or too late.
   This can cause unnecessary cache misses or stale data.
   I would use System.nanoTime in both put and get api and use the same time unit for TTL.

7. [Question, P1] Concurrent reads and writes (Lines 31-43)
   Both get and put in ConcurrentHashMap are thread-safe.
   Using put is fine here since we are just replacing the value.
   If we need to update a value based on its current value, I would use compute to make the update atomic.

   I would clarify the consistency requirements between get and put api.
   If another thread updates the same key while get is running, get may still return the old value.
   If returning an older value during concurrent updates is acceptable, I would keep the current implementation.
   If reads and writes for the same key must not overlap, I would use per-key locking in both get and put.
   Using a single lock for the entire cache can block requests for different keys and reduce throughput, especially with thousands of reads per second.
   I would also consider lock striping to limit the number of locks.

8. [Question, P3] TTL check timing (Lines 35–38)
   I would clarify when we should check the TTL. 
   The result may be different when the get method is called or when we actually check the entry.
   For example, if get is called just before the TTL expires, but cache.get(key) is delayed, the entry may expire before we check the TTL.
   In this case, we would return null even though the entry was valid when the request started.
   
   If the requirement is to check whether the entry was valid at the beginning of the request, I would record the current time at the beginning of get.
   Otherwise, I would keep the current approach.

9. [P0] Expired entries (Lines 35–43)
   We should remove expired entries.
   Even if an entry is expired, the map still holds a reference to it, so GC cannot free its memory.
   If we keep adding new keys, memory usage will keep growing.
   This can increase GC pressure, slow down the application, and eventually cause OutOfMemoryError.

   I would use cache.remove(key, entry) instead of cache.remove(key).
   Otherwise, we may remove a new entry added by another thread.

   I would also add periodic cleanup because some expired entries may never be read again.
   I would run the cleanup in a background thread and avoid cleaning up too many entries at once as it can slow down requests.

10. [Question, P1] Purpose of size api (Lines 45–47)
    I would clarify the purpose of size api.
    Currently it returns all stored entries, including expired ones.
    If we expect only valid entries, this can give us an incorrect count.
    If we just need to know how many entries are stored, I would keep it.

    I would not use size alone to enforce a strict size limit with concurrent writes.
    I would also consider renaming the method depending on its purpose, for example, totalEntryCount() or validEntryCount().

11. [Question, P1] Duplicate database calls on cache misses (Lines 32–44)
    I would check how callers handle a cache miss.
    If multiple threads try to get the same expired key at the same time, they may all load the same data from the database.
    This can increase database load and response time.
    If this is a concern, I would allow only one thread per key to load the data while other threads wait for the result.

12. [P2] Monitoring, Debugging, Logging (Lines 10, 31–47)
    I would add metrics for cache hits, misses, evictions, and cache size.
    Without these metrics, it would be difficult to know if the cache is working well or using too much memory in production.
    I would avoid adding a global lock just to collect metrics.

13. [Question, P1] Local cache per instance (Line 11)
    If the service runs on multiple instances, each instance will have its own cache and may return different values.
    If we need cross-instance consistency, I would consider sharing invalidation events or using a shared cache such as Redis.
    Otherwise, I would keep the local cache and document the expected period of stale data.

14. [Question, P2] Mutable keys (Lines 10, 31–36)
    I would document that keys must keep the same equals and hashCode behavior while stored in the cache.
    If a key changes after insertion, we may not be able to find or remove it, which can cause unexpected cache misses or retained entries.

15. [P3] External library
    If we can use an external library, I would also consider Caffeine.
    It already supports expiration, size-based eviction, concurrent loading, and cache statistics.
    I would compare it with the custom implementation before deciding.
