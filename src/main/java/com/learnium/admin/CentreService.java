package com.learnium.admin;

import com.learnium.api.dto.AdminDtos.CentreDto;
import com.learnium.api.dto.AdminDtos.UpdateCentreRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Centre;
import com.learnium.repository.user.CentreRepository;
import com.learnium.security.LearniumPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own centre profile (name + branding fields used on reports). */
@Service
public class CentreService {

    private final CentreRepository centreRepository;

    public CentreService(CentreRepository centreRepository) {
        this.centreRepository = centreRepository;
    }

    @Transactional(readOnly = true)
    public CentreDto getCentre(LearniumPrincipal principal) {
        return toDto(loadOwnCentre(principal));
    }

    @Transactional
    public CentreDto updateCentre(LearniumPrincipal principal, UpdateCentreRequest request) {
        Centre centre = loadOwnCentre(principal);
        centre.setName(request.name());
        centre.setLogoUrl(request.logoUrl());
        centre.setContactEmail(request.contactEmail());
        return toDto(centreRepository.save(centre));
    }

    private Centre loadOwnCentre(LearniumPrincipal principal) {
        return centreRepository
                .findById(principal.centreId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
    }

    private static CentreDto toDto(Centre centre) {
        return new CentreDto(
                centre.getCentreId(), centre.getName(), centre.getLogoUrl(), centre.getContactEmail());
    }
}
