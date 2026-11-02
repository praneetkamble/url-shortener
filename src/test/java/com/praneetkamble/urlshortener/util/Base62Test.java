package com.praneetkamble.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Base62Test {

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "9,9", "10,A", "35,Z", "36,a", "61,z", "62,10", "3844,100", "100000,Q0u"})
    void encodesKnownValues(long value, String expected) {
        assertThat(Base62.encode(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 12345, 100_000, 987_654_321L, Long.MAX_VALUE})
    void decodeInvertsEncode(long value) {
        assertThat(Base62.decode(Base62.encode(value))).isEqualTo(value);
    }

    @Test
    void encodingIsInjectiveForARange() {
        Set<String> seen = new HashSet<>();
        for (long i = 0; i < 50_000; i++) {
            assertThat(seen.add(Base62.encode(i))).isTrue();
        }
    }

    @Test
    void producesOnlyUrlSafeCharacters() {
        assertThat(Base62.encode(Long.MAX_VALUE)).matches("[0-9A-Za-z]+");
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> Base62.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIllegalCharactersAndEmptyInput() {
        assertThatThrownBy(() -> Base62.decode("ab-c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> Base62.decode("zzzzzzzzzzzzz")).isInstanceOf(IllegalArgumentException.class);
    }
}
