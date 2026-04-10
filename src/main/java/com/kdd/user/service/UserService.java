package com.kdd.user.service;

import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.user.dto.CreateProfileRequest;
import com.kdd.user.dto.UpdateProfileRequest;
import com.kdd.user.dto.UserResponse;
import com.kdd.user.entity.*;
import com.kdd.user.repository.StaffProfileRepository;
import com.kdd.user.repository.StudentProfileRepository;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StaffProfileRepository staffProfileRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = findUserById(userId);
        StudentProfile studentProfile = null;
        StaffProfile staffProfile = null;

        if (user.isProfileCompleted()) {
            if (user.getUserType() == UserType.STUDENT) {
                studentProfile = studentProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED));
            } else if (user.getUserType() == UserType.STAFF) {
                staffProfile = staffProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED));
            }
        }

        return UserResponse.from(user, studentProfile, staffProfile);
    }

    @Transactional
    public UserResponse createProfile(Long userId, CreateProfileRequest request) {
        User user = findUserById(userId);

        if (user.isProfileCompleted()) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_COMPLETED);
        }

        UserType userType = parseUserType(request.userType());
        user.completeProfile(userType);
        user.updateName(request.name());

        StudentProfile studentProfile = null;
        StaffProfile staffProfile = null;

        try {
            if (userType == UserType.STUDENT) {
                validateStudentFields(request);
                studentProfile = createStudentProfile(user, request);
            } else {
                validateStaffFields(request);
                staffProfile = createStaffProfile(user, request);
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_COMPLETED);
        }

        return UserResponse.from(user, studentProfile, staffProfile);
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        if (!user.isProfileCompleted()) {
            throw new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED);
        }

        user.updateName(request.name());

        StudentProfile studentProfile = null;
        StaffProfile staffProfile = null;

        if (user.getUserType() == UserType.STUDENT) {
            studentProfile = studentProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED));
            studentProfile.update(
                    request.studentId(),
                    request.department() != null ? parseStudentDepartment(request.department()) : null,
                    request.grade(),
                    request.admissionYear(),
                    request.academicStatus() != null ? parseAcademicStatus(request.academicStatus()) : null,
                    request.additionalInfo()
            );
        } else if (user.getUserType() == UserType.STAFF) {
            staffProfile = staffProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_COMPLETED));
            staffProfile.update(
                    request.staffDepartment() != null ? parseStaffDepartment(request.staffDepartment()) : null,
                    request.jobDescription()
            );
        }

        return UserResponse.from(user, studentProfile, staffProfile);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateStudentFields(CreateProfileRequest request) {
        if (request.studentId() == null || request.studentId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.department() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.grade() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.admissionYear() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateStaffFields(CreateProfileRequest request) {
        if (request.staffDepartment() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private StudentProfile createStudentProfile(User user, CreateProfileRequest request) {
        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .studentId(request.studentId())
                .department(parseStudentDepartment(request.department()))
                .grade(request.grade())
                .admissionYear(request.admissionYear())
                .academicStatus(request.academicStatus() != null
                        ? parseAcademicStatus(request.academicStatus())
                        : AcademicStatus.ENROLLED)
                .additionalInfo(request.additionalInfo())
                .build();
        return studentProfileRepository.save(profile);
    }

    private StaffProfile createStaffProfile(User user, CreateProfileRequest request) {
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .department(parseStaffDepartment(request.staffDepartment()))
                .jobDescription(request.jobDescription())
                .build();
        return staffProfileRepository.save(profile);
    }

    private UserType parseUserType(String value) {
        try {
            return UserType.from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private StudentDepartment parseStudentDepartment(String value) {
        try {
            return StudentDepartment.from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private StaffDepartment parseStaffDepartment(String value) {
        try {
            return StaffDepartment.from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private AcademicStatus parseAcademicStatus(String value) {
        try {
            return AcademicStatus.from(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
