package com.example.savings.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "savings_account")
public class SavingsAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 12)
	private String accountNumber;

	@Column(name = "customer_name", nullable = false, length = 100)
	private String customerName;

	@Column(name = "nickname", length = 30)
	private String nickname;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SavingsAccount() {
		// for JPA
	}

	public SavingsAccount(String accountNumber, String customerName, String nickname) {
		this.accountNumber = accountNumber;
		this.customerName = customerName;
		this.nickname = nickname;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getNickname() {
		return nickname;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
