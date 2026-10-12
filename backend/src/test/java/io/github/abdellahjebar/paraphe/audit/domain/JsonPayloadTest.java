package io.github.abdellahjebar.paraphe.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonPayloadTest {

    @Test
    void buildsAFlatObjectInOrder() {
        assertThat(JsonPayload.of("statusCode", "VAO", "effect", "APPROVING_WITH_RESERVES"))
                .isEqualTo("{\"statusCode\":\"VAO\",\"effect\":\"APPROVING_WITH_RESERVES\"}");
    }

    @Test
    void emptyInputGivesAnEmptyObject() {
        assertThat(JsonPayload.of()).isEqualTo("{}");
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertThat(JsonPayload.of("k", "a\"b\\c\nd\te\u0001"))
                .isEqualTo("{\"k\":\"a\\\"b\\\\c\\nd\\te\\u0001\"}");
    }

    @Test
    void rejectsAnOddNumberOfArguments() {
        assertThatThrownBy(() -> JsonPayload.of("only-a-key"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
