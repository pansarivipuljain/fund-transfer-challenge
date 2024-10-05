package com.dws.challenge.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.AccountNotExistsException;
import com.dws.challenge.exception.DuplicateAccountIdException;

@Repository
public class AccountsRepositoryInMemory implements AccountsRepository {

	private final Map<String, Account> accounts = new ConcurrentHashMap<>();

	@Override
	public void createAccount(Account account) throws DuplicateAccountIdException {
		Account previousAccount = accounts.putIfAbsent(account.getAccountId(), account);
		if (previousAccount != null) {
			throw new DuplicateAccountIdException("Account id " + account.getAccountId() + " already exists!");
		}
	}

	@Override
	public Account getAccount(String accountId) {
		Account account = accounts.get(accountId);
		if (ObjectUtils.isEmpty(account)) {
			throw new AccountNotExistsException("Account id " + accountId + " does not exist");
		}

		return accounts.get(accountId);
	}

	@Override
	public void clearAccounts() {
		accounts.clear();
	}

	@Override
	public Account updateAccount(Account account) {
		accounts.put(account.getAccountId(), account);
		return account;
	}

}
