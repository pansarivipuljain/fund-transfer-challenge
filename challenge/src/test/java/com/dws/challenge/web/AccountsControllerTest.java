package com.dws.challenge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
@Slf4j
class AccountsControllerTest {

	private MockMvc mockMvc;

	@Autowired
	private AccountsService accountsService;

	@MockBean
	NotificationService notificationService;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeEach
	void prepareMockMvc() {
		this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

		// Reset the existing accounts before each test.
		accountsService.getAccountsRepository().clearAccounts();
	}

	@Test
	void createAccount() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

		Account account = accountsService.getAccount("Id-123");
		assertThat(account.getAccountId()).isEqualTo("Id-123");
		assertThat(account.getBalance()).isEqualByComparingTo("1000");
	}

	@Test
	void createDuplicateAccount() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
	}

	@Test
	void createAccountNoAccountId() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"balance\":1000}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createAccountNoBalance() throws Exception {
		this.mockMvc.perform(
				post("/v1/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"Id-123\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createAccountNoBody() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createAccountNegativeBalance() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
	}

	@Test
	void createAccountEmptyAccountId() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
	}

	@Test
	void getAccount() throws Exception {
		String uniqueAccountId = "Id-" + System.currentTimeMillis();
		Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
		this.accountsService.createAccount(account);
		this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId)).andExpect(status().isOk())
				.andExpect(content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
	}

	@Test
	void getAccountDoesNotExist() throws Exception {
		String uniqueAccountId = "Id-" + System.currentTimeMillis();
		this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId)).andExpect(status().isBadRequest());
	}

	@Test
	void transferAmountSuccess() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":2000}")).andExpect(status().isCreated());
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-124\",\"balance\":10}")).andExpect(status().isCreated());
		// Mocking notificationService
		doNothing().when(notificationService).notifyAboutTransfer(any(), any());
		this.mockMvc
				.perform(post("/v1/accounts/fundTransfer").contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountFrom\":\"Id-123\",\"accountTo\":\"Id-124\",\"amount\":\"1000\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void transferAmountAccountNotExist() throws Exception {
		this.mockMvc
				.perform(post("/v1/accounts/fundTransfer").contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountFrom\":\"Id-123\",\"accountTo\":\"Id-124\",\"amount\":\"1000\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void transferAmountWithNegativeNumber() throws Exception {
		this.mockMvc
				.perform(post("/v1/accounts/fundTransfer").contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountFrom\":\"Id-123\",\"accountTo\":\"Id-124\",\"amount\":\"-5000\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void transferAmountWithinSameAccount() throws Exception {
		this.mockMvc
				.perform(post("/v1/accounts/fundTransfer").contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountFrom\":\"Id-123\",\"accountTo\":\"Id-123\",\"amount\":\"1000\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void transferAmountOverdraft() throws Exception {
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-123\",\"balance\":2000}")).andExpect(status().isCreated());
		this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":\"Id-124\",\"balance\":10}")).andExpect(status().isCreated());
		this.mockMvc
				.perform(post("/v1/accounts/fundTransfer").contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountFrom\":\"Id-123\",\"accountTo\":\"Id-124\",\"amount\":\"4000\"}"))
				.andExpect(status().isBadRequest());
	}
}
