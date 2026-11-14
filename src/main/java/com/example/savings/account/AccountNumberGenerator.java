package com.example.savings.account;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates 12 digit account numbers: 11 random digits followed by a Luhn check digit,
 * so obvious typos in an account number can be caught without a database lookup.
 * <p>
 * TODO: in a real bank account numbers would come from the core banking system (or at least
 *  include a branch / product prefix) rather than being random.
 */
@Component
public class AccountNumberGenerator {

	static final int LENGTH = 12;

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder digits = new StringBuilder(LENGTH);
		digits.append(1 + random.nextInt(9)); // no leading zero
		for (int i = 1; i < LENGTH - 1; i++) {
			digits.append(random.nextInt(10));
		}
		digits.append(luhnCheckDigit(digits));
		return digits.toString();
	}

	static int luhnCheckDigit(CharSequence digits) {
		int sum = 0;
		boolean doubleIt = true; // rightmost digit of the payload gets doubled
		for (int i = digits.length() - 1; i >= 0; i--) {
			int d = digits.charAt(i) - '0';
			if (doubleIt) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			sum += d;
			doubleIt = !doubleIt;
		}
		return (10 - (sum % 10)) % 10;
	}

	static boolean isValid(String accountNumber) {
		if (accountNumber == null || !accountNumber.matches("\\d{" + LENGTH + "}")) {
			return false;
		}
		int expected = luhnCheckDigit(accountNumber.substring(0, LENGTH - 1));
		return accountNumber.charAt(LENGTH - 1) - '0' == expected;
	}
}
