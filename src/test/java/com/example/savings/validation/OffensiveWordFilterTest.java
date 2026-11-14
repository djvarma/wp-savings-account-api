package com.example.savings.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OffensiveWordFilterTest {

	private final OffensiveWordFilter filter = new OffensiveWordFilter(new ClassPathResource("offensive-nicknames.txt"));

	@ParameterizedTest
	@ValueSource(strings = {
			"idiot",
			"IDIOT",
			"Big Idiot",
			"my_idiot_account",
			"1d10t",
			"i.d.i.o.t",
			"stup1d savings",
			"$tupid"
	})
	void flagsOffensiveNicknames(String nickname) {
		assertThat(filter.isOffensive(nickname)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Holiday fund",
			"Rainy day savings",
			"Classic Savings", // contains "ass", but not as a word
			"Scunthorpe trip",
			"Assessment fees",
			"Dickens collection"
	})
	void allowsCleanNicknames(String nickname) {
		assertThat(filter.isOffensive(nickname)).isFalse();
	}

	@Test
	void nullAndBlankAreNotOffensive() {
		assertThat(filter.isOffensive(null)).isFalse();
		assertThat(filter.isOffensive("   ")).isFalse();
	}

	@Test
	void ignoresCommentsAndBlankLinesInWordList() {
		String list = """
				# a comment
				badword

				  Rude
				""";
		OffensiveWordFilter custom = new OffensiveWordFilter(new ByteArrayResource(list.getBytes(StandardCharsets.UTF_8)));

		assertThat(custom.isOffensive("so rude")).isTrue();
		assertThat(custom.isOffensive("BADWORD")).isTrue();
		assertThat(custom.isOffensive("a comment")).isFalse();
	}
}
