package com.learnium.learniumbackend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.learnium.learniumbackend.dto.ProfileData;
import com.learnium.learniumbackend.model.*;
import com.learnium.learniumbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserCertificateRepository userCertificateRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserLinkRepository userLinkRepository;

    @Autowired
    public ProfileService(UserRepository userRepository,
                         UserSkillRepository userSkillRepository,
                         UserCertificateRepository userCertificateRepository,
                         UserEducationRepository userEducationRepository,
                         UserLinkRepository userLinkRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.userCertificateRepository = userCertificateRepository;
        this.userEducationRepository = userEducationRepository;
        this.userLinkRepository = userLinkRepository;
    }

    public ProfileData getProfileByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isEmpty()) {
            throw new IllegalArgumentException("Firebase UID must not be null or empty");
        }
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("User not found for the given Firebase UID"));
        UUID userId = user.getId();
        List<ProfileData.SkillDTO> skills = userSkillRepository.findByUserId(userId).stream()
                .filter(Objects::nonNull)
                .map(skill -> ProfileData.SkillDTO.builder()
                        .name(skill.getName())
                        .level(skill.getLevel())
                        .build())
                .collect(Collectors.toList());
        List<ProfileData.CertificateDTO> certificates = userCertificateRepository.findByUserId(userId).stream()
                .filter(Objects::nonNull)
                .map(cert -> ProfileData.CertificateDTO.builder()
                        .id(cert.getId() != null ? cert.getId().toString() : null)
                        .title(cert.getTitle())
                        .issuer(cert.getIssuer())
                        .date(cert.getDate() != null ? cert.getDate().toString() : null)
                        .build())
                .collect(Collectors.toList());
        List<ProfileData.EducationDTO> education = userEducationRepository.findByUserId(userId).stream()
                .filter(Objects::nonNull)
                .map(edu -> ProfileData.EducationDTO.builder()
                        .id(edu.getId() != null ? edu.getId().toString() : null)
                        .institution(edu.getInstitution())
                        .grade(edu.getGrade())
                        .startDate(edu.getStartDate())
                        .current(edu.isCurrent())
                        .build())
                .collect(Collectors.toList());
        List<ProfileData.LinkDTO> links = userLinkRepository.findByUserId(userId).stream()
                .filter(Objects::nonNull)
                .map(link -> ProfileData.LinkDTO.builder()
                        .id(link.getId() != null ? link.getId().toString() : null)
                        .platform(link.getPlatform())
                        .url(link.getUrl())
                        .build())
                .collect(Collectors.toList());
        return ProfileData.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .grade(user.getGradeLevel())
                .school(user.getSchool())
                .bio(user.getBio())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .city(user.getCity())
                .state(user.getState())
                .country(user.getCountry())
                .totalPoints(user.getTotalPoints())
                .currentStreak(user.getCurrentStreak())
                .longestStreak(user.getLongestStreak())
                .problemsSolved(user.getProblemsSolved())
                .badgesEarned(user.getBadgesEarned())
                .skills(skills)
                .certificates(certificates)
                .education(education)
                .links(links)
                .isProfileComplete(user.isProfileComplete())
                .completionPercentage(user.getCompletionPercentage())
                .build();
    }

    public ProfileData getProfileByIdToken(String idToken) {
        if (idToken == null || idToken.isEmpty()) {
            throw new IllegalArgumentException("ID token must not be null or empty");
        }
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String firebaseUid = decodedToken.getUid();
            return getProfileByFirebaseUid(firebaseUid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Firebase token", e);
        }
    }
}
