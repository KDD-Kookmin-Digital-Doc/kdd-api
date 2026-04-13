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
        if (user.getUserType() == UserType.STUDENT) {
            return studentProfileRepository.findById(user.getId())
                    .map(p -> "%s %d학년 %s".formatted(
                            p.getDepartment().getDisplayName(),
                            p.getGrade(),
                            p.getAcademicStatus().getDisplayName()))
                    .orElseGet(user::getName);
        }

        return staffProfileRepository.findById(user.getId())
                .map(p -> "%s 직원".formatted(p.getDepartment().getDisplayName()))
                .orElseGet(user::getName);
    }
}
