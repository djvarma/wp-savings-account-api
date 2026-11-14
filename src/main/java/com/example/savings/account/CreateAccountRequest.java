package com.example.savings.account;

import com.example.savings.validation.NotOffensive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(

		@NotBlank(message = "customerName is required")
		@Size(max = 100, message = "customerName must be at most 100 characters")
		String customerName,

		// Optional. Letters, digits and a few separators, with single spaces between words
		// (so "     " or " padded " can't be used to sneak past the length check).
		@Size(min = 5, max = 30, message = "nickname must be between 5 and 30 characters")
		@Pattern(regexp = "[\\p{L}\\p{N}._'-]+( [\\p{L}\\p{N}._'-]+)*",
				message = "nickname can only contain letters, numbers, single spaces and . _ ' -")
		@NotOffensive
		String nickname
) {
}
