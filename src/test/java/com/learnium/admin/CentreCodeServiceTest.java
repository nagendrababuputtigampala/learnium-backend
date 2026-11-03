package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.learnium.repository.user.CentreRepository;
import org.junit.jupiter.api.Test;

class CentreCodeServiceTest {
    private final CentreRepository centres = mock(CentreRepository.class);
    private final CentreCodeService service = new CentreCodeService(centres);

    @Test
    void derivesShortCodesFromNames() {
        assertThat(CentreCodeService.base("Demo Tutoring Centre")).isEqualTo("dtc");
        assertThat(CentreCodeService.base("École Lumière")).isEqualTo("el");
        assertThat(CentreCodeService.base("Learnium")).isEqualTo("learnium");
        assertThat(CentreCodeService.base(null)).isEqualTo("centre");
        assertThat(CentreCodeService.base("abcdefghijklmnopq")).isEqualTo("abcdefghijklmnop");
    }

    @Test
    void returnsTheBaseWhenFreeAndSuffixesCollisions() {
        when(centres.existsByCode("dtc")).thenReturn(false);
        assertThat(service.issue("Demo Tutoring Centre")).isEqualTo("dtc");

        when(centres.existsByCode("dtc")).thenReturn(true);
        when(centres.existsByCode("dtc2")).thenReturn(true);
        when(centres.existsByCode("dtc3")).thenReturn(false);
        assertThat(service.issue("Demo Tutoring Centre")).isEqualTo("dtc3");
    }

    @Test
    void keepsASuffixedCodeInsideTheColumnLimit() {
        when(centres.existsByCode("abcdefghijklmnop")).thenReturn(true);
        when(centres.existsByCode("abcdefghijklmno2")).thenReturn(false);
        assertThat(service.issue("abcdefghijklmnopq")).isEqualTo("abcdefghijklmno2");
    }
}
