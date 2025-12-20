package com.learnium.learniumbackend.service.auth;

import com.learnium.learniumbackend.entity.request.auth.AuthBootstrapRequest;
import com.learnium.learniumbackend.entity.response.auth.AuthBootstrapResponse;
import com.learnium.learniumbackend.entity.response.auth.AuthBootstrapResult;
import com.learnium.learniumbackend.exception.ValidationException;
import com.learnium.learniumbackend.mapper.auth.AuthBootstrapMapper;
import com.learnium.learniumbackend.model.v1.common.AuthProvider;
import com.learnium.learniumbackend.model.v1.common.UserStatus;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.model.v1.user.StudentProfile;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import com.learnium.learniumbackend.repository.v1.master.GradeRepository;
import com.learnium.learniumbackend.repository.v1.user.StudentProfileRepository;
import com.learnium.learniumbackend.repository.v1.user.UserAccountRepository;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTokenVerifier tokenVerifier;
    private final UserAccountRepository userRepo;
    private final StudentProfileRepository studentRepo;
    private final GradeRepository gradeRepo;
    private final AuthBootstrapMapper mapper;

    @Transactional
    public AuthBootstrapResponse bootstrap(String bearerToken, AuthBootstrapRequest request) {
        VerifiedAuthToken verified = tokenVerifier.verify(bearerToken);
        var dbProvider = toDbProvider(verified.getProvider());
        var existing = userRepo.findByAuthProviderAndAuthUid(dbProvider, verified.getUid());
        boolean isNewUser = existing.isEmpty();
        UserAccount user = existing.orElseGet(() -> {
            UserAccount userAccount = new UserAccount();
            userAccount.setAuthProvider(dbProvider);
            userAccount.setAuthUid(verified.getUid());
            return userAccount;
        });

        if (StringUtils.isNotBlank(verified.getEmail())) user.setEmail(verified.getEmail());
        if (StringUtils.isNotBlank(verified.getDisplayName())) user.setDisplayName(verified.getDisplayName());
        //if (StringUtils.hasText(verified.getPhotoUrl())) user.setPhotoUrl(verified.getPhotoUrl());
        user = userRepo.save(user);
        StudentProfile profile = studentRepo.findByUserId(user.getUserId()).orElse(null);
        if (ObjectUtils.isEmpty(profile)) {
            profile = new StudentProfile();
            profile.setUser(user);
        }

        if (ObjectUtils.isNotEmpty(request) && ObjectUtils.isNotEmpty(request.getStudent())) {
            if (StringUtils.isNotBlank(request.getStudent().getStudentName())) {
                profile.setFullName(request.getStudent().getStudentName());
            }
            if (StringUtils.isNotBlank(request.getStudent().getGradeCode())) {
                Grade grade = gradeRepo.findByCode(request.getStudent().getGradeCode())
                        .orElseThrow(() -> new ValidationException("Unknown gradeCode: " + request.getStudent().getGradeCode()));
                profile.setGrade(grade);
            }
        }else if(isNewUser){
            Grade defaultGrade = gradeRepo.findByCode("G1")
                    .orElseThrow(() -> new ValidationException("Default grade G1 not found"));
            profile.setFullName(user.getDisplayName());
            profile.setActive(user.getStatus() == UserStatus.ACTIVE);
            profile.setGrade(defaultGrade);

        }
        profile = studentRepo.save(profile);

        List<String> roles = List.of("STUDENT");
        List<String> scopes = List.of("READ_CONTENT", "TAKE_EXAMS", "VIEW_DASHBOARD");

        Map<String, Boolean> flags = Map.of(
                "enablePercentile", true,
                "enableBadges", true,
                "enableFlashcards", true,
                "enableFillBlanks", true
        );

        var result =  new AuthBootstrapResult(isNewUser, verified, user, profile, roles, scopes, flags);
        return mapper.toResponse(result);
    }

    private AuthProvider toDbProvider(VerifiedAuthToken.AuthProvider p) {
        return switch (p) {
            case GOOGLE -> AuthProvider.google;
            case EMAIL -> AuthProvider.email;
            case APPLE -> AuthProvider.facebook;
        };
    }
}