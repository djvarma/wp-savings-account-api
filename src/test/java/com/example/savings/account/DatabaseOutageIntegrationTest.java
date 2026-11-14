package com.example.savings.account;

import com.example.savings.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stops the Postgres container part way through to check the API degrades sensibly.
 * Uses its own application context (different properties) and throws it away afterwards,
 * so the stopped database doesn't leak into other tests.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class DatabaseOutageIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void returns503WhenDatabaseIsDownButStillServesCachedAccounts() throws Exception {
		String body = createAccount()
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		// warm the cache
		mockMvc.perform(get("/api/v1/accounts/{id}", id)).andExpect(status().isOk());

		postgres.stop();

		// The first requests after the outage get a pooled connection that is already dead,
		// later ones fail to get a connection at all. Both should be a 503.
		for (int i = 0; i < 3; i++) {
			createAccount()
					.andExpect(status().isServiceUnavailable())
					.andExpect(header().string("Retry-After", "30"))
					.andExpect(jsonPath("$.title").value("Service unavailable"));

			mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
					.andExpect(status().isServiceUnavailable());
		}

		// still served from Redis
		mockMvc.perform(get("/api/v1/accounts/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
	}

	private ResultActions createAccount() throws Exception {
		return mockMvc.perform(post("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"customerName\": \"Jane Doe\"}"));
	}
}
