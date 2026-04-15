package com.kdd.chat.service;

import com.kdd.user.entity.User;
import com.kdd.user.entity.UserType;
import com.kdd.user.repository.StaffProfileRepository;
import com.kdd.user.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContextBuilder {

    private final StudentProfileRepository studentProfileRepository;
    private final StaffProfileRepository staffProfileRepository;

    public String buildContext(User user) {
        // 컨텍스트 생성 실패가 메시지 전송 전체 실패로 전이되지 않도록, 필드 누락 시 NPE 대신 이름 폴백
        if (user.getUserType() == UserType.STUDENT) {
            return studentProfileRepository.findById(user.getId())
                    .filter(p -> p.getDepartment() != null
                            && p.getGrade() != null
                            && p.getAcademicStatus() != null)
                    .map(p -> "%s %d학년 %s".formatted(
                            p.getDepartment().getDisplayName(),
                            p.getGrade(),
                            p.getAcademicStatus().getDisplayName()))
                    .orElseGet(user::getName);
        }

        return staffProfileRepository.findById(user.getId())
                .filter(p -> p.getDepartment() != null)
                .map(p -> "%s 직원".formatted(p.getDepartment().getDisplayName()))
                .orElseGet(user::getName);
    }
}
