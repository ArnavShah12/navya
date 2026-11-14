package com.navya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.domain.Category;
import com.navya.domain.Condition;
import com.navya.domain.Money;
import com.navya.exception.InvalidInputException;
import com.navya.util.Ids;
import com.navya.util.Validate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidateTest {
    @ParameterizedTest(name = "[{index}] valid email {0}")
    @ValueSource(strings = {"a@b.co", "  Mixed.Case@Example.COM ", "x+tag@sub.domain.in"})
    @DisplayName("TC-01 valid email addresses are accepted and lower-cased")
    void goodEmails(String e) { assertThat(Validate.email(e)).isEqualTo(e.trim().toLowerCase()); }

    @ParameterizedTest(name = "[{index}] bad email {0}")
    @ValueSource(strings = {"", "   ", "no-at-sign", "a@b", "a b@c.com", "@x.com"})
    @DisplayName("TC-02 malformed email addresses are rejected")
    void badEmails(String e) { assertThatThrownBy(() -> Validate.email(e)).isInstanceOf(InvalidInputException.class); }

    @Test
    @DisplayName("TC-03 password length boundaries 7, 8, 64, 65")
    void passwordBoundaries() {
        assertThatThrownBy(() -> Validate.password("1234567")).isInstanceOf(InvalidInputException.class);
        assertThat(Validate.password("12345678")).hasSize(8);
        assertThat(Validate.password("x".repeat(64))).hasSize(64);
        assertThatThrownBy(() -> Validate.password("x".repeat(65))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> Validate.password(null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-04 title length boundaries 0, 1, 80, 81")
    void titleBoundaries() {
        assertThatThrownBy(() -> Validate.text("", "Title", 1, 80)).isInstanceOf(InvalidInputException.class);
        assertThat(Validate.text("a", "Title", 1, 80)).isEqualTo("a");
        assertThat(Validate.text("a".repeat(80), "Title", 1, 80)).hasSize(80);
        assertThatThrownBy(() -> Validate.text("a".repeat(81), "Title", 1, 80)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-05 price boundaries -1, 0, 10,000,000, 10,000,001 rupees")
    void priceBoundaries() {
        assertThatThrownBy(() -> Money.toPaise(-1)).isInstanceOf(InvalidInputException.class);
        assertThat(Money.toPaise(0)).isZero();
        assertThat(Money.toPaise(10_000_000)).isEqualTo(1_000_000_000L);
        assertThatThrownBy(() -> Money.toPaise(10_000_001)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-06 category and condition accept the listed values only")
    void enums() {
        assertThat(Category.parse(" shoes ")).isEqualTo(Category.SHOES);
        assertThat(Condition.parse("LIKE NEW")).isEqualTo(Condition.LIKE_NEW);
        assertThatThrownBy(() -> Category.parse("Cars")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> Condition.parse(null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("Money formats rupees with Indian grouping not required, paise only when present")
    void moneyFormat() {
        assertThat(Money.format(125_000)).isEqualTo("Rs 1,250");
        assertThat(Money.format(4_950)).isEqualTo("Rs 49.50");
    }

    @Test
    @DisplayName("Ids accept UUIDs only")
    void ids() {
        assertThat(Ids.valid(Ids.newId())).isTrue();
        assertThat(Ids.valid("1; DROP TABLE users")).isFalse();
        assertThat(Ids.valid(null)).isFalse();
    }
}
