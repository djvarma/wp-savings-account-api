package com.example.savings.account;

import com.example.savings.validation.OffensiveWordFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavingsAccountController.class)
@Import(OffensiveWordFilter.class)
class SavingsAccountControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SavingsAccountService accountService;

	@Test
	void createReturns201WithLocation() throws Exception {
		AccountResponse account = sampleAccount();
		when(accountService.createAccount(any())).thenReturn(account);

		postAccount("""
				{"customerName": "Jane Doe", "nickname": "Holiday fund"}
				""")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/accounts/" + account.id())))
				.andExpect(jsonPath("$.id").value(account.id().toString()))
				.andExpect(jsonPath("$.accountNumber").value(account.accountNumber()))
				.andExpect(jsonPath("$.customerName").value("Jane Doe"))
				.andExpect(jsonPath("$.nickname").value("Holiday fund"));
	}

	@Test
	void nicknameIsOptional() throws Exception {
		when(accountService.createAccount(any())).thenReturn(sampleAccount());

		postAccount("""
				{"customerName": "Jane Doe"}
				""")
				.andExpect(status().isCreated());
	}

	@Test
	void customerNameIsRequired() throws Exception {
		postAccount("""
				{"nickname": "Holiday fund"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.errors[0].field").value("customerName"))
				.andExpect(jsonPath("$.errors[0].message").value("customerName is required"));

		verifyNoInteractions(accountService);
	}

	@Test
	void blankCustomerNameIsRejected() throws Exception {
		postAccount("""
				{"customerName": "   "}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("customerName"));
	}

	@Test
	void nicknameShorterThanFiveCharactersIsRejected() throws Exception {
		postAccount("""
				{"customerName": "Jane Doe", "nickname": "Hols"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nickname"))
				.andExpect(jsonPath("$.errors[0].message").value("nickname must be between 5 and 30 characters"));
	}

	@Test
	void nicknameLongerThanThirtyCharactersIsRejected() throws Exception {
		postAccount("""
				{"customerName": "Jane Doe", "nickname": "%s"}
				""".formatted("a".repeat(31)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nickname"));
	}

	@Test
	void nicknameOfOnlySpacesIsRejected() throws Exception {
		postAccount("""
				{"customerName": "Jane Doe", "nickname": "      "}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nickname"));
	}

	@Test
	void offensiveNicknameIsRejected() throws Exception {
		postAccount("""
				{"customerName": "Jane Doe", "nickname": "Stupid savings"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nickname"))
				.andExpect(jsonPath("$.errors[0].message").value("nickname contains offensive language"));

		verifyNoInteractions(accountService);
	}

	@Test
	void malformedJsonIsRejected() throws Exception {
		postAccount("{\"customerName\": ")
				.andExpect(status().isBadRequest());
	}

	@Test
	void accountLimitReturns422() throws Exception {
		when(accountService.createAccount(any())).thenThrow(new AccountLimitExceededException(5));

		postAccount("""
				{"customerName": "Jane Doe"}
				""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Account limit reached"));
	}

	@Test
	void databaseDownReturns503() throws Exception {
		when(accountService.createAccount(any()))
				.thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

		postAccount("""
				{"customerName": "Jane Doe"}
				""")
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "30"))
				.andExpect(jsonPath("$.title").value("Service unavailable"));
	}

	@Test
	void getReturnsAccount() throws Exception {
		AccountResponse account = sampleAccount();
		when(accountService.getAccount(account.id())).thenReturn(account);

		mockMvc.perform(get("/api/v1/accounts/{id}", account.id()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(account.accountNumber()));
	}

	@Test
	void getUnknownAccountReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(accountService.getAccount(id)).thenThrow(new AccountNotFoundException(id));

		mockMvc.perform(get("/api/v1/accounts/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Account not found"));
	}

	@Test
	void getWithInvalidIdReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/not-a-uuid"))
				.andExpect(status().isBadRequest());
	}

	private ResultActions postAccount(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private static AccountResponse sampleAccount() {
		return new AccountResponse(UUID.randomUUID(), "123456789012", "Jane Doe", "Holiday fund", Instant.now());
	}
}
