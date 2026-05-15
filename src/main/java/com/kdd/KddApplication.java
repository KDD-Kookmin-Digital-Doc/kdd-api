package com.kdd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class KddApplication {
    public static void main(String[] args) {
        // JVM 기본 타임존을 Asia/Seoul로 고정한다.
        // - 컨테이너 OS의 TZ env에 의존하지 않도록 코드에서 명시.
        // - LocalDateTime.now() / Hibernate LocalDateTime 매핑 / @Scheduled zone이 같은
        //   타임존을 보도록 통일해, DB의 expires_at·revoked_at·created_at과 cleanup 잡 cutoff
        //   계산이 일관되게 KST 기준이 되도록 한다 (#69 cleanup, #76 grace window 관련).
        // - SpringApplication.run 이전에 설정해야 컨텍스트 초기화 중 호출되는 now()도 같은 zone을 본다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(KddApplication.class, args);
    }
}
