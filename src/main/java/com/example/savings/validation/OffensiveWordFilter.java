package com.example.savings.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks text against a list of offensive words loaded from the classpath.
 * <p>
 * Matching is done on whole words, after lower-casing and undoing common character substitutions
 * (e.g. "1d10t" -> "idiot"). Whole-word matching avoids rejecting innocent names that happen to
 * contain a bad word ("Classic Savings" contains "ass"). The text is also checked with all
 * separators removed, to catch things like "i.d.i.o.t" or "id_iot".
 * <p>
 * TODO: this won't catch everything (repeated letters, other languages, creative spellings).
 *  A real implementation would use a maintained list or a moderation service, and the list
 *  should be reloadable without a deploy.
 */
@Component
public class OffensiveWordFilter {

	private final Set<String> offensiveWords;

	public OffensiveWordFilter(@Value("${app.offensive-words-file:classpath:offensive-nicknames.txt}") Resource wordList) {
		this.offensiveWords = load(wordList);
	}

	public boolean isOffensive(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String normalised = undoSubstitutions(text.toLowerCase(Locale.ROOT));

		boolean anyWordOffensive = Arrays.stream(normalised.split("[^a-z]+"))
				.anyMatch(offensiveWords::contains);

		return anyWordOffensive || offensiveWords.contains(normalised.replaceAll("[^a-z]", ""));
	}

	private static String undoSubstitutions(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			sb.append(switch (c) {
				case '0' -> 'o';
				case '1', '!' -> 'i';
				case '3' -> 'e';
				case '4', '@' -> 'a';
				case '5', '$' -> 's';
				case '7' -> 't';
				default -> c;
			});
		}
		return sb.toString();
	}

	private static Set<String> load(Resource resource) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(line -> line.toLowerCase(Locale.ROOT))
					.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not load offensive word list from " + resource, e);
		}
	}
}
