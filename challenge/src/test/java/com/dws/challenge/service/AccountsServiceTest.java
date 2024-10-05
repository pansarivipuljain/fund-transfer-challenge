package com.dws.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.Transaction;
import com.dws.challenge.exception.AccountNotExistsException;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.exception.InsufficientBalanceException;

import lombok.extern.slf4j.Slf4j;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
class AccountsServiceTest {

	@Autowired
	private AccountsService accountsService;

	@MockBean
	NotificationService notificationService;

	@BeforeEach
	void prepareMockMvc() {
		// Reset the existing accounts before each test.
		accountsService.getAccountsRepository().clearAccounts();
	}

	@Test
	void addAccount() {
		Account account = new Account("Id-123");
		account.setBalance(new BigDecimal(1000));
		this.accountsService.createAccount(account);

		assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
	}

	@Test
	void addAccount_failsOnDuplicateId() {
		String uniqueId = "Id-" + System.currentTimeMillis();
		Account account = new Account(uniqueId);
		this.accountsService.createAccount(account);

		try {
			this.accountsService.createAccount(account);
			fail("Should have failed when adding duplicate account");
		} catch (DuplicateAccountIdException ex) {
			assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
		}
	}

	@Test
	void transferAccount_failsWithinSameAccountId() {
		Transaction transaction = new Transaction("Id-125A", "Id-125A", new BigDecimal(1000));
		try {
			this.accountsService.transferAmount(transaction);
			fail("Should have failed when using same account in transfer");
		} catch (IllegalArgumentException ex) {
			assertThat(ex.getMessage()).isEqualTo("To and From account should not be same!");
		}
	}

	@Test
	void transferAccount_failsWithNonExistingAccount() {
		String accountId = "Id-127A";
		Transaction transaction = new Transaction(accountId, "Id-126A", new BigDecimal(1000));
		try {
			this.accountsService.transferAmount(transaction);
			fail("Should have failed when using not existing account in transfer");
		} catch (AccountNotExistsException ex) {
			assertThat(ex.getMessage()).isEqualTo("Account id " + accountId + " does not exist");
		}
	}

	@Test
	void transferAccount_failsWithOverdraft() {
		Account toAccount = new Account("Id-124", new BigDecimal(10));
		this.accountsService.createAccount(toAccount);
		Account fromAccount = new Account("Id-125", new BigDecimal(500));
		this.accountsService.createAccount(fromAccount);

		Transaction transaction = new Transaction(fromAccount.getAccountId(), toAccount.getAccountId(),
				new BigDecimal(1000));
		try {
			this.accountsService.transferAmount(transaction);
			fail("Should have failed when insufficient balance");
		} catch (InsufficientBalanceException ex) {
			assertThat(ex.getMessage()).isEqualTo("Insufficient balance!");
		}
	}

	/**
	 * This test will hit 5 concurrent request for fund transfer, if the service
	 * method "accountsService.transferAmount" is not synchronized then test will
	 * fail else pass
	 */
	@Test
	void transferAmountTestThreadSafty() throws Exception {
		Account toAccount = new Account("Id-124", new BigDecimal(10));
		this.accountsService.createAccount(toAccount);
		Account fromAccount = new Account("Id-125", new BigDecimal(5000));
		this.accountsService.createAccount(fromAccount);

		BigDecimal toAccountCurrentBalance = toAccount.getBalance();
		BigDecimal fromAccountCurrentBalance = fromAccount.getBalance();

		log.info("toAccount initial balance : {}", toAccountCurrentBalance);
		log.info("fromAccount initial balance : {}", fromAccountCurrentBalance);

		// Mocking notificationService
		doNothing().when(notificationService).notifyAboutTransfer(any(), any());

		// Thread Management using Executor service
		ExecutorService executor = Executors.newFixedThreadPool(5);
		CountDownLatch latch = new CountDownLatch(5);
		int amountTransferred = 0;
		for (int i = 0; i < 5; i++) {
			Random randomNumber = new Random();
			// Random number to generate in between 1 to 50
			int number = randomNumber.nextInt(50) + 1;
			amountTransferred += number;
			executor.submit(() -> {
				try {
					Transaction transaction = new Transaction(fromAccount.getAccountId(), toAccount.getAccountId(),
							new BigDecimal(number));
					this.accountsService.transferAmount(transaction);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			});
		}

		// Wait for all threads to finish
		latch.await();
		executor.shutdown();

		log.info("toAccount updateed balance : {}", this.accountsService.getAccount("Id-124").getBalance());
		log.info("fromAccount updated balance : {}", this.accountsService.getAccount("Id-125").getBalance());

		// Checking total amount correctly transferred to "toAccountt" holder in
		// concurrent requests
		assertThat(this.accountsService.getAccount("Id-124").getBalance())
				.isEqualTo(toAccountCurrentBalance.add(new BigDecimal(amountTransferred)));

		// Checking total amount debited from "fromAccount" in concurrent requests
		assertThat(this.accountsService.getAccount("Id-125").getBalance())
				.isEqualTo(fromAccountCurrentBalance.subtract(new BigDecimal(amountTransferred)));
	}

	/**
	 * Thread safety test with multiple account request. This test will hit 5
	 * concurrent request for fund transfer, if the service method
	 * "accountsService.transferAmount" is not synchronized then test will fail else
	 * pass
	 */
	@Test
	void transferAmountTestThreadSaftyWithMultipleAccount() throws Exception {
		Account fromAccountA = new Account("Id-125A", new BigDecimal(5000));
		this.accountsService.createAccount(fromAccountA);
		Account toAccountB = new Account("Id-125B", new BigDecimal(1000));
		this.accountsService.createAccount(toAccountB);

		Account fromAccountC = new Account("Id-125C", new BigDecimal(5000));
		this.accountsService.createAccount(fromAccountC);
		Account toAccountD = new Account("Id-125D", new BigDecimal(10));
		this.accountsService.createAccount(toAccountD);

		BigDecimal currentBalanceAccountA = fromAccountA.getBalance();
		BigDecimal currentBalanceAccountB = toAccountB.getBalance();
		BigDecimal currentBalanceAccountC = fromAccountC.getBalance();
		BigDecimal currentBalanceAccountD = toAccountD.getBalance();

		log.info("fromAccount initial balance : {}", currentBalanceAccountA);
		log.info("toAccount initial balance : {}", currentBalanceAccountB);
		log.info("fromAccount initial balance : {}", currentBalanceAccountC);
		log.info("toAccount initial balance : {}", currentBalanceAccountD);

		// Mocking notificationService
		doNothing().when(notificationService).notifyAboutTransfer(any(), any());

		// Thread Management using Executor service
		ExecutorService executor = Executors.newFixedThreadPool(6);
		CountDownLatch latch = new CountDownLatch(6);
		int amountTransferred = 0;
		for (int i = 0; i < 3; i++) {
			Random randomNumber = new Random();
			// Random number to generate in between 1 to 50
			int number = randomNumber.nextInt(50) + 1;
			amountTransferred += number;
			executor.submit(() -> {
				try {
					Transaction transaction = new Transaction(fromAccountA.getAccountId(), toAccountB.getAccountId(),
							new BigDecimal(number));
					this.accountsService.transferAmount(transaction);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			});

			executor.submit(() -> {
				try {
					Transaction transaction = new Transaction(fromAccountC.getAccountId(), toAccountD.getAccountId(),
							new BigDecimal(number));
					this.accountsService.transferAmount(transaction);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			});
		}

		// Wait for all threads to finish
		latch.await();
		executor.shutdown();

		log.info("fromAccount updated balance : {}", this.accountsService.getAccount("Id-125A").getBalance());
		log.info("toAccount updateed balance : {}", this.accountsService.getAccount("Id-125B").getBalance());
		log.info("fromAccount updated balance : {}", this.accountsService.getAccount("Id-125C").getBalance());
		log.info("toAccount updateed balance : {}", this.accountsService.getAccount("Id-125D").getBalance());

		// Checking total amount debited from "fromAccount" in concurrent requests
		assertThat(this.accountsService.getAccount("Id-125A").getBalance())
				.isEqualTo(currentBalanceAccountA.subtract(new BigDecimal(amountTransferred)));

		// Checking total amount correctly transferred to "toAccount" holder in
		// concurrent requests
		assertThat(this.accountsService.getAccount("Id-125B").getBalance())
				.isEqualTo(currentBalanceAccountB.add(new BigDecimal(amountTransferred)));

		// Checking total amount debited from "fromAccount" in concurrent requests
		assertThat(this.accountsService.getAccount("Id-125C").getBalance())
				.isEqualTo(currentBalanceAccountC.subtract(new BigDecimal(amountTransferred)));

		// Checking total amount correctly transferred to "toAccount" holder in
		// concurrent requests
		assertThat(this.accountsService.getAccount("Id-125D").getBalance())
				.isEqualTo(currentBalanceAccountD.add(new BigDecimal(amountTransferred)));
	}
}
