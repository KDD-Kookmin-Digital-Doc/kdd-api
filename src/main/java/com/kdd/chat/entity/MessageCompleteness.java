package com.kdd.chat.entity;

/**
 * AI 어시스턴트 메시지가 done 이벤트까지 정상 완료됐는지(`COMPLETE`),
 * done 없이 끊긴 부분 답변인지(`PARTIAL`) 구분한다. 호출처에서 boolean 리터럴을 흘리지 않도록 enum화.
 */
public enum MessageCompleteness {
    COMPLETE,
    PARTIAL
}
