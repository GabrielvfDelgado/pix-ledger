package io.github.gabrielvfdelgado.pixledger.adapters.out.redis;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyCache;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyRecord;
import io.github.gabrielvfdelgado.pixledger.application.service.Fingerprint;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Se o Redis falhar, a operação segue pelo PostgreSQL, que continua sendo a garantia. */
public final class RedisIdempotencyCache implements IdempotencyCache {

    private static final Logger LOG = LoggerFactory.getLogger(RedisIdempotencyCache.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redis;

    public RedisIdempotencyCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, IdempotencyKey key) {
        try {
            String value = redis.opsForValue().get(redisKey(scope, key));
            if (value == null) {
                return Optional.empty();
            }
            String[] parts = value.split(SEPARATOR, 2);
            return Optional.of(new IdempotencyRecord(parts[0], UUID.fromString(parts[1])));
        } catch (RuntimeException e) {
            LOG.warn("Cache de idempotência indisponível na leitura: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(String scope, IdempotencyKey key, IdempotencyRecord record) {
        try {
            redis.opsForValue().set(redisKey(scope, key), record.fingerprint() + SEPARATOR + record.resourceId(), TTL);
        } catch (RuntimeException e) {
            LOG.warn("Cache de idempotência indisponível na escrita: {}", e.getClass().getSimpleName());
        }
    }

    private static String redisKey(String scope, IdempotencyKey key) {
        return "idempotency:" + Fingerprint.of(scope, key.value());
    }
}
