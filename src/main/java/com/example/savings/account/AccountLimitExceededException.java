package com.example.savings.account;

public class AccountLimitExceededException extends RuntimeException {

	public AccountLimitExceededException(int limit) {
		super("A customer cannot have more than " + limit + " savings accounts");
	}
}
