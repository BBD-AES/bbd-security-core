package com.bbd.securitycore.application.port.in;

import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserSnapshot;

/*
 사용자 인가 검사를 수행하기 위한 inbound port.

 각 MSA는 컨트롤러나 서비스에서 이 유스케이스를 호출해
 현재 사용자가 해당 API나 리소스에 접근 가능한지 검사한다.

 실제 구현체는 application service에 두고,
 각 서비스는 구현체가 아니라 이 계약에 의존한다.
 */
public interface AuthorizeUserUseCase {

    /*
     사용자가 존재하고 ACTIVE 상태인지 검사한다.

     PENDING, INACTIVE 사용자는 ERP 기능을 사용할 수 없다.
     */
    void requireActive(UserSnapshot snapshot);

    /*
     사용자가 특정 역할을 가지고 있는지 검사한다.

     예:
     - HQ_MANAGER만 승인 가능
     - BRANCH_MANAGER만 지점 재고 조정 가능
     */
    void requireRole(UserSnapshot snapshot, UserRole requiredRole);

    /*
     사용자가 접근하려는 리소스의 소속과
     현재 사용자의 소속이 일치하는지 검사한다.

     HQ 사용자는 전체 소속 접근이 가능하고,
     BRANCH 사용자는 자신의 tenancyId와 같은 리소스에만 접근할 수 있다.
     */
    void requireSameTenancy(UserSnapshot snapshot, Long targetTenancyId);
}