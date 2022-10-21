package com.example.demo.event.vanilla;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GreetingEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishGreetingEvent(String message) {
        applicationEventPublisher.publishEvent(new GreetingEvent(this, message));
    }
}
