package com.ragadmin.server.common.web;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.function.Function;

public final class ServerSentEventSupport {

    private ServerSentEventSupport() {
    }

    public static <T> Flux<ServerSentEvent<T>> toEventStream(
            Flux<T> events,
            Function<T, String> eventTypeExtractor
    ) {
        return toEventStream(events, eventTypeExtractor, event -> null);
    }

    public static <T> Flux<ServerSentEvent<T>> toEventStream(
            Flux<T> events,
            Function<T, String> eventTypeExtractor,
            Function<T, String> eventIdExtractor
    ) {
        return events.map(event -> {
            ServerSentEvent.Builder<T> builder = ServerSentEvent.<T>builder()
                    .event(normalizeEventName(eventTypeExtractor.apply(event)))
                    .data(event);
            String eventId = eventIdExtractor.apply(event);
            if (StringUtils.hasText(eventId)) {
                builder.id(eventId);
            }
            return builder.build();
        });
    }

    private static String normalizeEventName(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "message";
        }
        return eventType.trim().toLowerCase(Locale.ROOT);
    }
}
