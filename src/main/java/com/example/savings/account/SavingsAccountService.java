package com.example.savings.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SavingsAccountService {

	public static final String ACCOUNTS_CACHE = "accounts";

	private static final Logger log = LoggerFactory.getLogger(SavingsAccountService.class);
	private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 5;

	private final SavingsAccountRepository repository;
	private final AccountNumberGenerator accountNumberGenerator;
	private final int maxAccountsPerCustomer;

	public SavingsAccountService(SavingsAccountRepository repository,
								 AccountNumberGenerator accountNumberGenerator,
								 @Value("${app.accounts.max-per-customer:5}") int maxAccountsPerCustomer) {
		this.repository = repository;
		this.accountNumberGenerator = accountNumberGenerator;
		this.maxAccountsPerCustomer = maxAccountsPerCustomer;
	}

	// TODO: retry when the transaction couldn't start (safe, nothing reached the database). Retrying after
	//  that could open a duplicate account if the commit actually went through, so it needs an
	//  Idempotency-Key first.
	@Transactional
	public AccountResponse createAccount(CreateAccountRequest request) {
		// There is no customer id in the requirements, so a customer is identified by their name
		// (case-insensitive, extra whitespace ignored).
		// TODO: use the authenticated customer's id instead. Two different people can share a name.
		String customerName = normaliseName(request.customerName());

		repository.lockCustomer(customerName);
		if (repository.countByCustomerName(customerName) >= maxAccountsPerCustomer) {
			throw new AccountLimitExceededException(maxAccountsPerCustomer);
		}

		SavingsAccount account = new SavingsAccount(nextAccountNumber(), customerName, request.nickname());
		account = repository.save(account);

		log.info("Created savings account {}", account.getId());
		return AccountResponse.from(account);
	}

	// TODO: retry transient connection errors (e.g. a dead pooled connection after a database failover)
	//  with a short backoff. Spring's @Retryable would do.
	@Cacheable(cacheNames = ACCOUNTS_CACHE, key = "#id")
	public AccountResponse getAccount(UUID id) {
		return repository.findAccount(id)
				.map(AccountResponse::from)
				.orElseThrow(() -> new AccountNotFoundException(id));
	}

	private String nextAccountNumber() {
		// Collisions are very unlikely (10^11 possible numbers), and the unique constraint on the
		// column is the real safety net. This just avoids failing the request in the common case.
		for (int attempt = 1; attempt <= MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
			String candidate = accountNumberGenerator.next();
			if (!repository.existsByAccountNumber(candidate)) {
				return candidate;
			}
			log.warn("Generated account number already exists, retrying (attempt {})", attempt);
		}
		throw new IllegalStateException("Could not generate a unique account number");
	}

	static String normaliseName(String name) {
		return name.trim().replaceAll("\\s+", " ");
	}
}
