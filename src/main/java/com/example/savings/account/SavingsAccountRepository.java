package com.example.savings.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID> {

	/**
	 * Same as findById, but as a declared query it runs without a transaction. Inside a transaction,
	 * if the connection drops, Hibernate's find() logs the error and returns null, which
	 * would turn a database outage into a 404.
	 */
	@Query("select a from SavingsAccount a where a.id = :id")
	Optional<SavingsAccount> findAccount(@Param("id") UUID id);

	@Query("select count(a) from SavingsAccount a where lower(a.customerName) = lower(:customerName)")
	long countByCustomerName(@Param("customerName") String customerName);

	boolean existsByAccountNumber(String accountNumber);

	/**
	 * Takes a transaction-scoped Postgres advisory lock for the customer, so two concurrent
	 * requests for the same customer can't both pass the "max accounts" check. The lock is
	 * released automatically when the transaction commits or rolls back.
	 */
	@Query(value = "select 1 from pg_advisory_xact_lock(hashtext(lower(:customerName)))", nativeQuery = true)
	int lockCustomer(@Param("customerName") String customerName);
}
