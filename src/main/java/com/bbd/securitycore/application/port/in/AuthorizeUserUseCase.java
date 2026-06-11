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

}