package org.genealogyarchive.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Status JSON is written by hand; user-supplied text must never break out of a string or into HTML. */
class FileControllerJsonTest {

    @Test
    void quotesEscapeJsonAndHtmlSignificantCharacters() {
        assertThat(FileController.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(FileController.quote("<script>&")).isEqualTo("\"\\u003cscript\\u003e\\u0026\"");
        assertThat(FileController.quote("line\nbreak\u0001")).isEqualTo("\"line\\u000abreak\\u0001\"");
        assertThat(FileController.quote("Ölgemälde")).isEqualTo("\"Ölgemälde\"");
    }
}
