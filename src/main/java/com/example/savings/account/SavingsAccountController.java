package com.example.savings.account;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

// TODO: authentication (e.g. OAuth2 resource server). Accounts should only be created for, and
//  read by, the logged-in customer. Also rate limit account creation.
@RestController
@RequestMapping("/api/v1/accounts")
public class SavingsAccountController {

	private final SavingsAccountService accountService;

	public SavingsAccountController(SavingsAccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping
	public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse account = accountService.createAccount(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(account.id())
				.toUri();
		return ResponseEntity.created(location).body(account);
	}

	@GetMapping("/{id}")
	public AccountResponse getAccount(@PathVariable UUID id) {
		return accountService.getAccount(id);
	}
}
