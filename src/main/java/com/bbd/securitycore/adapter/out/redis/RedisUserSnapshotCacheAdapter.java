package com.bbd.securitycore.adapter.out.redis;

import com.bbd.securitycore.application.port.out.LoadUserSnapshotCachePort;
import com.bbd.securitycore.application.port.out.SaveUserSnapshotCachePort;
import com.bbd.securitycore.config.BbdSecurityProperties;
import com.bbd.securitycore.domain.UserSnapshot;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

/*
 Redis에서 UserSnapshot 캐시를 조회하고 저장하는 adapter.

 bbd-security-core는 각 MSA가 매 요청마다 User Service를 직접 호출하지 않도록
 UserSnapshot을 Redis에 캐싱한다.

 캐시 key는 Keycloak sub 기준으로 만든다.

 예:
 user:snapshot:{keycloakSub}
 */
public class RedisUserSnapshotCacheAdapter implements LoadUserSnapshotCachePort, SaveUserSnapshotCachePort {

    private final RedisTemplate<String, UserSnapshot> redisTemplate;
    private final BbdSecurityProperties properties;

    public RedisUserSnapshotCacheAdapter(
            RedisTemplate<String, UserSnapshot> redisTemplate,
            BbdSecurityProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /*
     Keycloak sub 기준으로 Redis에서 UserSnapshot을 조회한다.

     캐시에 없으면 Optional.empty()를 반환한다.
     이후 application service가 User Service 조회로 fallback한다.
     */
    @Override
    public Optional<UserSnapshot> findByKeycloakSub(String keycloakSub) {
        if (keycloakSub == null || keycloakSub.isBlank()) {
            return Optional.empty();
        }

        UserSnapshot userSnapshot = redisTemplate.opsForValue().get(toKey(keycloakSub));

        return Optional.ofNullable(userSnapshot);
    }

    /*
     User Service에서 조회한 UserSnapshot을 Redis에 저장한다.

     TTL은 bbd.security.user-snapshot-cache-ttl-seconds 설정을 사용한다.
     기본값은 300초다.
     */
    @Override
    public void save(UserSnapshot userSnapshot) {
        if (userSnapshot == null || userSnapshot.keycloakSub() == null || userSnapshot.keycloakSub().isBlank()) {
            return;
        }

        redisTemplate.opsForValue().set(
                toKey(userSnapshot.keycloakSub()),
                userSnapshot,
                Duration.ofSeconds(properties.getUserSnapshotCacheTtlSeconds())
        );
    }

    private String toKey(String keycloakSub) {
        return properties.getUserSnapshotCacheKeyPrefix() + keycloakSub;
    }
}