package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.domain.ClickEvent;
import com.schwab.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickTrackingServiceTest {

  @Mock private ClickEventRepository clickEventRepository;

  @Test
  void recordsClickWithGivenDetails() {
    ClickTrackingService trackingService = new ClickTrackingService(clickEventRepository);
    ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);

    trackingService.recordClick(7L, "https://google.com", "curl/8.7.1");

    verify(clickEventRepository).save(captor.capture());
    assertThat(captor.getValue().getShortUrlId()).isEqualTo(7L);
    assertThat(captor.getValue().getReferrer()).isEqualTo("https://google.com");
    assertThat(captor.getValue().getUserAgent()).isEqualTo("curl/8.7.1");
  }

  @Test
  void swallowsRepositoryFailureRatherThanPropagating() {
    ClickTrackingService trackingService = new ClickTrackingService(clickEventRepository);
    when(clickEventRepository.save(any())).thenThrow(new RuntimeException("db unavailable"));

    // Analytics is best-effort: a write failure must never surface to the redirect caller.
    assertThatCode(() -> trackingService.recordClick(7L, null, null)).doesNotThrowAnyException();
  }
}
