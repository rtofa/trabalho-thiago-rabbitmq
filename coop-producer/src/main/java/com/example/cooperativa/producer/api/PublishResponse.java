package com.example.cooperativa.producer.api;

import com.example.cooperativa.common.contract.CooperativeEvent;

public record PublishResponse(
        CooperativeEvent event,
        boolean brokerConfirmed,
        boolean republished,
        String message
) {
}
