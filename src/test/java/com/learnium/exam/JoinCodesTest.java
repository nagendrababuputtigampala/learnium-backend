package com.learnium.exam;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.learnium.repository.exam.ExamInstanceRepository;
import org.junit.jupiter.api.Test;

class JoinCodesTest {
    @Test void retriesAnExistingCodeAndUsesEightUnambiguousCharacters() {
        var instances = mock(ExamInstanceRepository.class);
        when(instances.existsByJoinCode(anyString())).thenReturn(true, false);
        assertThat(new JoinCodes(instances).next()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
        verify(instances, times(2)).existsByJoinCode(anyString());
    }
}
