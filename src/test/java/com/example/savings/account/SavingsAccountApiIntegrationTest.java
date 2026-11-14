package com.example.savings.account;

import com.example.savings.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end tests against real Postgres and Redis containers (needs Docker).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SavingsAccountApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SavingsAccountService accountService;

	@Autowired
	SavingsAccountRepository repository;

	@Autowired
	StringRedisTemplate redis;

	@Test
	void createdAccountCanBeFetched() throws Exception {
		String customer = uniqueCustomer();

		String body = createAccount(customer, "Holiday fund")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountNumber").isString())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String accountNumber = JsonPath.read(body, "$.accountNumber");

		mockMvc.perform(get("/api/v1/accounts/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.customerName").value(customer))
				.andExpect(jsonPath("$.nickname").value("Holiday fund"))
				.andExpect(jsonPath("$.createdAt").isString());

		assertThat(AccountNumberGenerator.isValid(accountNumber)).isTrue();
	}

	@Test
	void getIsCachedInRedis() throws Exception {
		String body = createAccount(uniqueCustomer(), null).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		String cacheKey = SavingsAccountService.ACCOUNTS_CACHE + "::" + id;

		assertThat(redis.hasKey(cacheKey)).isFalse();

		mockMvc.perform(get("/api/v1/accounts/{id}", id)).andExpect(status().isOk());

		assertThat(redis.hasKey(cacheKey)).isTrue();
		assertThat(redis.opsForValue().get(cacheKey)).contains(id);
	}

	@Test
	void unknownAccountReturns404AndIsNotCached() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/accounts/{id}", id))
				.andExpect(status().isNotFound());

		assertThat(redis.hasKey(SavingsAccountService.ACCOUNTS_CACHE + "::" + id)).isFalse();
	}

	@Test
	void customerCannotHaveMoreThanFiveAccounts() throws Exception {
		String customer = uniqueCustomer();
		for (int i = 0; i < 5; i++) {
			createAccount(customer, null).andExpect(status().isCreated());
		}

		// same customer, different casing and spacing
		createAccount("  " + customer.toUpperCase() + " ", null)
				.andExpect(status().isUnprocessableContent());

		assertThat(repository.countByCustomerName(customer)).isEqualTo(5);
	}

	@Test
	void concurrentRequestsCannotExceedAccountLimit() throws Exception {
		String customer = uniqueCustomer();
		int attempts = 10;

		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			tasks.add(() -> {
				try {
					accountService.createAccount(new CreateAccountRequest(customer, null));
					return true;
				}
				catch (AccountLimitExceededException e) {
					return false;
				}
			});
		}

		int created = 0;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			for (Future<Boolean> result : executor.invokeAll(tasks)) {
				if (result.get()) {
					created++;
				}
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(created).isEqualTo(5);
		assertThat(repository.countByCustomerName(customer)).isEqualTo(5);
	}

	private ResultActions createAccount(String customerName, String nickname) throws Exception {
		String json = nickname == null
				? "{\"customerName\": \"%s\"}".formatted(customerName)
				: "{\"customerName\": \"%s\", \"nickname\": \"%s\"}".formatted(customerName, nickname);
		return mockMvc.perform(post("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	// The database is shared between tests, so give each test its own customer
	private static String uniqueCustomer() {
		return "Customer " + UUID.randomUUID().toString().substring(0, 8);
	}
}
