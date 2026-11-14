package com.example.savings.account;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

	private final AccountNumberGenerator generator = new AccountNumberGenerator();

	@RepeatedTest(50)
	void generatesTwelveDigitNumberWithValidCheckDigit() {
		String accountNumber = generator.next();

		assertThat(accountNumber).matches("[1-9]\\d{11}");
		assertThat(AccountNumberGenerator.isValid(accountNumber)).isTrue();
	}

	@Test
	void calculatesLuhnCheckDigit() {
		// well known example: 7992739871 -> check digit 3
		assertThat(AccountNumberGenerator.luhnCheckDigit("7992739871")).isEqualTo(3);
	}

	@Test
	void detectsSingleDigitTypo() {
		String accountNumber = generator.next();
		char first = accountNumber.charAt(0);
		String typo = (first == '9' ? '1' : (char) (first + 1)) + accountNumber.substring(1);

		assertThat(AccountNumberGenerator.isValid(typo)).isFalse();
	}

	@Test
	void numbersAreNotRepeatedInPractice() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			seen.add(generator.next());
		}
		assertThat(seen).hasSize(10_000);
	}
}
