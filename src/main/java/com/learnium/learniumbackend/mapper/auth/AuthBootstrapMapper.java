package com.learnium.learniumbackend.mapper.auth;

import com.learnium.learniumbackend.entity.response.auth.AuthBootstrapResponse;
import com.learnium.learniumbackend.entity.response.auth.AuthBootstrapResult;
import com.learnium.learniumbackend.model.v1.master.Grade;
import com.learnium.learniumbackend.model.v1.user.StudentProfile;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthBootstrapMapper {

    @Mapping(target = "serverTime", expression = "java(java.time.Instant.now())")
   // @Mapping(target = "newUser", source = "newUser")
    @Mapping(target = "auth", expression = "java(toAuthInfo(result))")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "studentProfile", source = "studentProfile")
    @Mapping(target = "permissions",
            expression = "java(AuthBootstrapResponse.PermissionsDto.builder().roles(result.getRoles()).scopes(result.getScopes()).build())")
    @Mapping(target = "app",
            expression = "java(AuthBootstrapResponse.AppDto.builder().featureFlags(result.getFeatureFlags()).build())")
    AuthBootstrapResponse toResponse(AuthBootstrapResult result);

    AuthBootstrapResponse.UserDto toUserDto(UserAccount user);

    @Mapping(target = "grade", source = "grade")
    AuthBootstrapResponse.StudentProfileDto toStudentProfileDto(StudentProfile sp);

    AuthBootstrapResponse.GradeDto toGradeDto(Grade grade);

    default AuthBootstrapResponse.AuthInfo toAuthInfo(AuthBootstrapResult result) {
        return AuthBootstrapResponse.AuthInfo.builder()
                .provider(result.getAuthToken().getProvider().name())
                .firebaseUid(result.getAuthToken().getUid())
                .emailVerified(result.getAuthToken().isEmailVerified())
                .build();
    }
}