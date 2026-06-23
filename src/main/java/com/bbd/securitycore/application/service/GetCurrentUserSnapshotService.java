package com.bbd.securitycore.application.service;

import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.application.port.out.ExtractAuthenticatedUserPort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotCachePort;
import com.bbd.securitycore.application.port.out.LoadUserSnapshotPort;
import com.bbd.securitycore.application.port.out.SaveUserSnapshotCachePort;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;
import com.bbd.securitycore.global.logging.SecurityLogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/*
 현재 요청 사용자에 대한 UserSnapshot을 조회하는 application service.

 이 클래스는 각 MSA에서 인가 판단을 하기 전에
 현재 인증 사용자의 인가 정보를 가져오는 역할을 한다.

 조회 흐름은 다음과 같다.

 1. 현재 요청의 인증 정보에서 keycloakSub 추출
 2. Redis 캐시에서 UserSnapshot 조회
 3. 캐시에 없으면 User Service에서 UserSnapshot 조회
 4. User Service에서 조회한 결과를 Redis 캐시에 저장
 5. application 결과 모델로 반환
 */
@Slf4j
@RequiredArgsConstructor
public class GetCurrentUserSnapshotService implements GetCurrentUserSnapshotUseCase {

    private final ExtractAuthenticatedUserPort extractAuthenticatedUserPort;
    private final LoadUserSnapshotCachePort loadUserSnapshotCachePort;
    private final LoadUserSnapshotPort loadUserSnapshotPort;
    private final SaveUserSnapshotCachePort saveUserSnapshotCachePort;

    /*
     현재 인증된 사용자의 UserSnapshot을 조회한다.

     keycloakSub가 없으면 인증되지 않은 요청으로 판단한다.

     Redis 캐시 포트가 등록되어 있으면 먼저 캐시에서 UserSnapshot을 조회하고,
     캐시에 없으면 User Service에서 조회한다.

     Redis 캐시 포트가 없으면 캐시를 사용하지 않고
     바로 User Service에서 UserSnapshot을 조회한다.
     */
    @Override
    public CurrentUserSnapshotResult getCurrentUserSnapshot() {
        String keycloakSub = extractAuthenticatedUserPort.getCurrentKeycloakSub();

        if (keycloakSub == null || keycloakSub.isBlank()) {
            log.warn("UserSnapshot 조회 실패: 현재 인증 사용자의 keycloakSub가 없습니다.");
            throw new ApiException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        UserSnapshot snapshot;

        if (loadUserSnapshotCachePort != null) {
            snapshot = loadFromCache(keycloakSub)
                    .orElseGet(() -> loadFromUserServiceAndCache(keycloakSub));
        } else {
            log.debug(
                    "UserSnapshot 캐시 포트가 없어 User Service 원본 조회를 수행합니다. keycloakSubHash={}",
                    keycloakSubHash(keycloakSub)
            );
            snapshot = loadFromUserServiceAndCache(keycloakSub);
        }

        return CurrentUserSnapshotResult.from(snapshot);
    }

    /*
     Redis 같은 캐시 저장소는 인가 판단의 원본이 아니다.

     캐시 조회 중 Redis 연결 실패 같은 런타임 예외가 발생하면
     캐시 miss와 동일하게 취급해 User Service 원본 조회로 fallback한다.
     */
    private Optional<UserSnapshot> loadFromCache(String keycloakSub) {
        try {
            Optional<UserSnapshot> cachedSnapshot = loadUserSnapshotCachePort.findByKeycloakSub(keycloakSub);

            if (cachedSnapshot.isPresent()) {
                UserSnapshot snapshot = cachedSnapshot.get();
                log.debug(
                        "UserSnapshot 캐시 hit. keycloakSubHash={}, userId={}, status={}, role={}",
                        keycloakSubHash(keycloakSub),
                        snapshot.userId(),
                        snapshot.status(),
                        snapshot.role()
                );
            } else {
                log.debug(
                        "UserSnapshot 캐시 miss. User Service 원본 조회로 fallback합니다. keycloakSubHash={}",
                        keycloakSubHash(keycloakSub)
                );
            }

            return cachedSnapshot;
        } catch (RuntimeException exception) {
            log.warn(
                    "UserSnapshot 캐시 조회에 실패했습니다. User Service 원본 조회로 fallback합니다. keycloakSubHash={}",
                    keycloakSubHash(keycloakSub),
                    exception
            );
            return Optional.empty();
        }
    }

    /*
     Redis 캐시에 UserSnapshot이 없거나 캐시를 사용할 수 없을 때
     User Service에서 UserSnapshot을 조회한다.

     User Service에서도 사용자를 찾지 못하면 인가 판단을 진행할 수 없으므로
     USER_SNAPSHOT_NOT_FOUND 예외를 던진다.

     저장용 Redis 캐시 포트가 등록되어 있으면
     정상적으로 조회된 UserSnapshot을 이후 요청에서 재사용할 수 있도록 저장한다.
     */
    private UserSnapshot loadFromUserServiceAndCache(String keycloakSub) {
        log.debug(
                "UserSnapshot을 User Service에서 조회합니다. keycloakSubHash={}",
                keycloakSubHash(keycloakSub)
        );

        UserSnapshot snapshot = loadUserSnapshotPort.loadByKeycloakSub(keycloakSub);

        if (snapshot == null) {
            log.warn(
                    "UserSnapshot 조회 실패: User Service에서 사용자를 찾지 못했습니다. keycloakSubHash={}",
                    keycloakSubHash(keycloakSub)
            );
            throw new ApiException(ErrorCode.USER_SNAPSHOT_NOT_FOUND);
        }

        log.debug(
                "UserSnapshot User Service 조회 성공. keycloakSubHash={}, userId={}, status={}, role={}",
                keycloakSubHash(keycloakSub),
                snapshot.userId(),
                snapshot.status(),
                snapshot.role()
        );

        if (saveUserSnapshotCachePort != null) {
            saveToCache(snapshot);
        } else {
            log.debug(
                    "UserSnapshot 캐시 저장 포트가 없어 저장을 건너뜁니다. keycloakSubHash={}, userId={}",
                    keycloakSubHash(keycloakSub),
                    snapshot.userId()
            );
        }

        return snapshot;
    }

    /*
     User Service 원본 조회가 성공했다면 현재 요청은 계속 처리해야 한다.

     Redis 저장 실패는 캐시 warm-up 실패일 뿐이므로 로그만 남기고 전파하지 않는다.
     */
    private void saveToCache(UserSnapshot snapshot) {
        try {
            saveUserSnapshotCachePort.save(snapshot);
        } catch (RuntimeException exception) {
            log.warn(
                    "UserSnapshot 캐시 저장에 실패했습니다. 현재 요청은 User Service 조회 결과로 계속 처리합니다. keycloakSubHash={}, userId={}",
                    keycloakSubHash(snapshot.keycloakSub()),
                    snapshot.userId(),
                    exception
            );
        }
    }

    private String keycloakSubHash(String keycloakSub) {
        return SecurityLogUtils.fingerprint(keycloakSub);
    }
}
