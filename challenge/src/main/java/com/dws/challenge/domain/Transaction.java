package com.dws.challenge.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class Transaction {

	@NotNull
	@NotEmpty
	private final String accountFrom;

	@NotNull
	@NotEmpty
	private final String accountTo;

	@NotNull
	@Positive(message = "Amount must be positive number.")
	private BigDecimal amount;

	@JsonCreator
	public Transaction(@JsonProperty("accountFrom") String accountFrom, @JsonProperty("accountTo") String accountTo,
			@JsonProperty("amount") BigDecimal amount) {
		this.accountFrom = accountFrom;
		this.accountTo = accountTo;
		this.amount = amount;

	}
}
