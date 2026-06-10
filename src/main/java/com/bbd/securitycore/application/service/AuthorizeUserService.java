package com.bbd.securitycore.application.service;

import com.bbd.securitycore.application.port.in.AuthorizeUserUseCase;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;
import com.bbd.securitycore.global.error.ApiException;
import com.bbd.securitycore.global.error.dto.ErrorCode;

/*
 사용자 인가 규칙을 실제로 검사하는 application service.

 이 클래스는 각 MSA에서 공통으로 사용할 수 있는 인가 판단 로직을 담당한다.

 예를 들어 다음과 같은 검사를 수행한다.

 - 현재 사용자가 ACTIVE 상태인지
 - 특정 역할을 가지고 있는지
 - 접근하려는 리소스와 같은 소속인지

 컨트롤러나 각 서비스가 직접 if 문으로 인가 조건을 반복하지 않도록,
 공통 인가 판단 로직을 이곳에 모아둔다.
 */
public class AuthorizeUserService implements AuthorizeUserUseCase {

    /*
     사용자가 ERP 기능을 사용할 수 있는 활성 상태인지 검사한다.

     UserSnapshot이 없다는 것은 현재 요청 사용자에 대한 인가 정보를 찾을 수 없다는 뜻이다.
     PENDING 사용자는 승인 대기 상태이므로 기능 사용을 막고,
     INACTIVE 사용자는 비활성화된 상태이므로 기능 사용을 막는다.
     */
    @Override
    public void requireActive(UserSnapshot snapshot) {
        if (snapshot == null) {
            throw new ApiException(ErrorCode.USER_SNAPSHOT_NOT_FOUND);
        }

        if (snapshot.isPending()) {
            throw new ApiException(ErrorCode.USER_PENDING);
        }

        if (!snapshot.isActive()) {
            throw new ApiException(ErrorCode.USER_INACTIVE);
        }
    }

    /*
     사용자가 특정 역할을 가지고 있는지 검사한다.

     먼저 ACTIVE 상태인지 확인한 뒤,
     필요한 역할이 없으면 FORBIDDEN_ROLE 예외를 던진다.

     예:
     - HQ_MANAGER만 승인 가능
     - BRANCH_MANAGER만 지점 재고 조정 가능
     */
    @Override
    public void requireRole(UserSnapshot snapshot, UserRole requiredRole) {
        requireActive(snapshot);

        if (!snapshot.hasRole(requiredRole)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE);
        }
    }

    /*
     사용자가 접근하려는 리소스의 소속에 접근 가능한지 검사한다.

     HQ 사용자는 전체 소속 데이터에 접근할 수 있고,
     BRANCH 사용자는 자신의 tenancyId와 같은 리소스에만 접근할 수 있다.

     targetTenancyId는 각 MSA의 도메인 리소스에서 가져온 소속 ID이다.
     예:
     - salesOrder.getTenancyId()
     - stock.getTenancyId()
     - purchaseOrder.getTenancyId()
     */
    @Override
    public void requireSameTenancy(UserSnapshot snapshot, Long targetTenancyId) {
        requireActive(snapshot);

        if (!snapshot.canAccessTenancy(targetTenancyId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_TENANCY);
        }
    }
}