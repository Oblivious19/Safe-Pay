package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyExecutionResultTest {

    @Test
    void representsNewlyExecutedResponseIdentity() {
        IdempotencyExecutionResult<String> result =
                IdempotencyExecutionResult.executed(
                        201,
                        "{\"transactionId\":\"101\"}",
                        101L);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.responseBody())
                .isEqualTo("{\"transactionId\":\"101\"}");
        assertThat(result.transactionId()).isEqualTo(101L);
        assertThat(result.replayed()).isFalse();
    }

    @Test
    void representsReplayWithoutChangingOriginalIdentity() {
        IdempotencyExecutionResult<String> result =
                IdempotencyExecutionResult.replayed(
                        200,
                        "{\"transactionId\":\"202\"}",
                        202L);

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.responseBody())
                .isEqualTo("{\"transactionId\":\"202\"}");
        assertThat(result.transactionId()).isEqualTo(202L);
        assertThat(result.replayed()).isTrue();
    }

    @Test
    void permitsBodylessResponseButRejectsInvalidIdentity() {
        assertThat(IdempotencyExecutionResult.executed(
                204,
                null,
                null).responseBody()).isNull();

        assertThatThrownBy(() ->
                IdempotencyExecutionResult.executed(
                        99,
                        "response",
                        1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "httpStatus must be between 100 and 599");

        assertThatThrownBy(() ->
                IdempotencyExecutionResult.replayed(
                        200,
                        "response",
                        0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transactionId must be positive when supplied");
    }
}
