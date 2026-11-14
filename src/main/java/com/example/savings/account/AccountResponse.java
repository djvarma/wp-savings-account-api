package com.example.savings.account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
		UUID id,
		String accountNumber,
		String customerName,
		String nickname,
		Instant createdAt
) {

	static AccountResponse from(SavingsAccount account) {
		return new AccountResponse(
				account.getId(),
				account.getAccountNumber(),
				account.getCustomerName(),
				account.getNickname(),
				account.getCreatedAt());
	}
}
