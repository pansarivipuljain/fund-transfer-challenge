package com.dws.challenge.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.dws.challenge.constants.NotificationConstants;
import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.Transaction;
import com.dws.challenge.exception.AccountNotExistsException;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.repository.AccountsRepository;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccountsService {

	@Getter
	private final AccountsRepository accountsRepository;

	public AccountsService(AccountsRepository accountsRepository) {
		this.accountsRepository = accountsRepository;
	}

	@Autowired
	private NotificationService notificationService;

	public void createAccount(Account account) {
		this.accountsRepository.createAccount(account);
	}

	public Account getAccount(String accountId) {
		return this.accountsRepository.getAccount(accountId);
	}

	/**
	 * Transfer amount from one account to another in thread safe manner
	 * 
	 * @param transaction
	 */
	public synchronized void transferAmount(Transaction transaction) {
		Account fromAccount = this.accountsRepository.getAccount(transaction.getAccountFrom());
		Account toAccount = this.accountsRepository.getAccount(transaction.getAccountTo());

		if (ObjectUtils.isEmpty(fromAccount)) {
			throw new AccountNotExistsException("From Account not found");
		}

		if (ObjectUtils.isEmpty(toAccount)) {
			throw new AccountNotExistsException("To Account not found");
		}

		BigDecimal fromAccountBalance = fromAccount.getBalance();
		BigDecimal toAccountBalance = toAccount.getBalance();

		if (fromAccountBalance.compareTo(BigDecimal.ZERO) == 1
				&& fromAccountBalance.compareTo(transaction.getAmount()) == 1) {

			// Debit amount
			fromAccount.setBalance(fromAccountBalance.subtract(transaction.getAmount()));
			this.accountsRepository.updateAccount(fromAccount);
			log.info("updated balance for fromAccount : {}", fromAccount.getBalance());

			// Credit amount
			toAccount.setBalance(toAccountBalance.add(transaction.getAmount()));
			this.accountsRepository.updateAccount(toAccount);
			log.info("updated balance for toAccount : {}", toAccount.getBalance());


			// send notification
			log.info("Sending mail to both account holder ...");
			sendNotification(transaction, fromAccount, toAccount);

		} else {
			throw new InsufficientBalanceException("Insufficient balance!");
		}
	}

	/**
	 * Send notification using notificationService
	 * 
	 * @param transferAccount
	 * @param transferFrom
	 * @param transferTo
	 */
	private void sendNotification(Transaction transferAccount, Account transferFrom, Account transferTo) {
		notificationService.notifyAboutTransfer(transferTo,
				String.format(NotificationConstants.TO_ACCOUNT_MESSAGE, transferAccount.getAmount().toString(),
						transferFrom.getAccountId(), transferTo.getBalance().toString()));
		notificationService.notifyAboutTransfer(transferFrom,
				String.format(NotificationConstants.FROM_ACCOUNT_MESSAGE, transferAccount.getAmount().toString(),
						transferTo.getAccountId(), transferFrom.getBalance().toString()));
	}
}
