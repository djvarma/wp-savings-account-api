package com.example.savings.web;

import com.example.savings.account.AccountLimitExceededException;
import com.example.savings.account.AccountNotFoundException;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.Comparator;
import java.util.List;

/**
 * Turns exceptions into consistent JSON error responses. Spring's base class already covers the
 * standard MVC errors (malformed JSON, bad path variables, unsupported media type and so on).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String RETRY_AFTER_SECONDS = "30";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
																  HttpHeaders headers,
																  HttpStatusCode status,
																  WebRequest request) {
		List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
				.toList();

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Invalid request");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ProblemDetail handleNotFound(AccountNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Account not found");
		return problem;
	}

	@ExceptionHandler(AccountLimitExceededException.class)
	ProblemDetail handleAccountLimit(AccountLimitExceededException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
		problem.setTitle("Account limit reached");
		return problem;
	}

	/**
	 * Database is down, unreachable or timing out. Tell the client to try again later rather
	 * than returning a generic 500.
	 */
	@ExceptionHandler({
			CannotCreateTransactionException.class,
			DataAccessResourceFailureException.class,
			TransientDataAccessException.class
	})
	ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception ex) {
		log.error("Database unavailable", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable, please try again later");
		problem.setTitle("Service unavailable");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
				.body(problem);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		// If the database drops while a pooled connection is in use, the error can come back
		// wrapped in something generic (e.g. a failed rollback), so look at the root causes too.
		if (isConnectionFailure(ex)) {
			return handleDatabaseUnavailable(ex);
		}

		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side");
		problem.setTitle("Internal server error");
		return ResponseEntity.internalServerError().body(problem);
	}

	private static boolean isConnectionFailure(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof SQLTransientConnectionException || t instanceof SQLRecoverableException) {
				return true;
			}
			if (t instanceof JDBCConnectionException) {
				return true;
			}
			// Commit/rollback failed. In practice this means the connection died mid-transaction,
			// and Spring reports the rollback failure instead of the original error.
			if (t instanceof org.hibernate.TransactionException) {
				return true;
			}
			// SQLState class 08 = connection exception
			if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
				return true;
			}
		}
		return false;
	}

	record FieldError(String field, String message) {
	}
}
