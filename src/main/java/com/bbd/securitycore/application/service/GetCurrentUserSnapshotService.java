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
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class GetCurrentUserSnapshotService implements GetCurrentUserSnapshotUseCase {

    private final ExtractAuthenticatedUserPort extractAuthenticatedUserPort;
    private final LoadUserSnapshotCachePort loadUserSnapshotCachePort;
    private final LoadUserSnapshotPort loadUserSnapshotPort;
    private final SaveUserSnapshotCachePort saveUserSnapshotCachePort;

    /*
     현재 인증된 사용자의 UserSnapshot을 조회한다.

     keycloakSub가 없으면 인증되지 않은 요청으로 판단한다.
     캐시에 UserSnapshot이 있으면 그대로 사용하고,
     캐시에 없으면 User Service에서 조회한 뒤 캐시에 저장한다.
     */
    @Override
    public CurrentUserSnapshotResult getCurrentUserSnapshot() {
        String keycloakSub = extractAuthenticatedUserPort.getCurrentKeycloakSub();

        if (keycloakSub == null || keycloakSub.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        UserSnapshot snapshot = loadUserSnapshotCachePort.findByKeycloakSub(keycloakSub)
                .orElseGet(() -> loadFromUserServiceAndCache(keycloakSub));

        return CurrentUserSnapshotResult.from(snapshot);
    }

    /*
     Redis 캐시에 UserSnapshot이 없을 때 User Service에서 조회한다.

     User Service에서도 사용자를 찾지 못하면 인가 판단을 진행할 수 없으므로
     USER_SNAPSHOT_NOT_FOUND 예외를 던진다.

     정상적으로 조회된 UserSnapshot은 이후 요청에서 재사용할 수 있도록 캐시에 저장한다.
     */
    private UserSnapshot loadFromUserServiceAndCache(String keycloakSub) {
        UserSnapshot snapshot = loadUserSnapshotPort.loadByKeycloakSub(keycloakSub);

        if (snapshot == null) {
            throw new ApiException(ErrorCode.USER_SNAPSHOT_NOT_FOUND);
        }

        saveUserSnapshotCachePort.save(snapshot);

        return snapshot;
    }
}