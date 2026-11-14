package com.example.savings.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsAccountServiceTest {

	@Mock
	SavingsAccountRepository repository;

	@Mock
	AccountNumberGenerator accountNumberGenerator;

	SavingsAccountService service;

	@BeforeEach
	void setUp() {
		service = new SavingsAccountService(repository, accountNumberGenerator, 5);
	}

	@Test
	void createsAccountWithGeneratedNumber() {
		when(repository.countByCustomerName("Jane Doe")).thenReturn(2L);
		when(accountNumberGenerator.next()).thenReturn("123456789012");
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AccountResponse response = service.createAccount(new CreateAccountRequest("Jane Doe", "Holiday fund"));

		assertThat(response.accountNumber()).isEqualTo("123456789012");
		assertThat(response.customerName()).isEqualTo("Jane Doe");
		assertThat(response.nickname()).isEqualTo("Holiday fund");
	}

	@Test
	void locksCustomerBeforeCheckingAccountCount() {
		when(accountNumberGenerator.next()).thenReturn("123456789012");
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.createAccount(new CreateAccountRequest("Jane Doe", null));

		InOrder inOrder = inOrder(repository);
		inOrder.verify(repository).lockCustomer("Jane Doe");
		inOrder.verify(repository).countByCustomerName("Jane Doe");
		inOrder.verify(repository).save(any());
	}

	@Test
	void normalisesWhitespaceInCustomerName() {
		when(accountNumberGenerator.next()).thenReturn("123456789012");
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.createAccount(new CreateAccountRequest("  Jane   Doe ", null));

		ArgumentCaptor<SavingsAccount> saved = ArgumentCaptor.forClass(SavingsAccount.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getCustomerName()).isEqualTo("Jane Doe");
		verify(repository).countByCustomerName("Jane Doe");
	}

	@Test
	void rejectsSixthAccountForSameCustomer() {
		when(repository.countByCustomerName("Jane Doe")).thenReturn(5L);

		assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest("Jane Doe", null)))
				.isInstanceOf(AccountLimitExceededException.class)
				.hasMessageContaining("5");

		verify(repository, never()).save(any());
	}

	@Test
	void retriesWhenGeneratedAccountNumberAlreadyExists() {
		when(accountNumberGenerator.next()).thenReturn("111111111111", "222222222222");
		when(repository.existsByAccountNumber("111111111111")).thenReturn(true);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AccountResponse response = service.createAccount(new CreateAccountRequest("Jane Doe", null));

		assertThat(response.accountNumber()).isEqualTo("222222222222");
	}

	@Test
	void givesUpAfterTooManyAccountNumberCollisions() {
		when(accountNumberGenerator.next()).thenReturn("111111111111");
		when(repository.existsByAccountNumber(anyString())).thenReturn(true);

		assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest("Jane Doe", null)))
				.isInstanceOf(IllegalStateException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void getAccountThrowsWhenMissing() {
		UUID id = UUID.randomUUID();
		when(repository.findAccount(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getAccount(id))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessageContaining(id.toString());
	}
}
