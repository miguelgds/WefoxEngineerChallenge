package com.technicaltest.payments.application.usecases;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.technicaltest.payments.application.repositories.AccountRepository;
import com.technicaltest.payments.application.repositories.PaymentErrorsRepository;
import com.technicaltest.payments.application.repositories.PaymentGatewayRepository;
import com.technicaltest.payments.application.repositories.PaymentRepository;
import com.technicaltest.payments.application.services.PaymentsService;
import com.technicaltest.payments.domain.entities.Account;
import com.technicaltest.payments.domain.entities.Payment;
import com.technicaltest.payments.domain.entities.PaymentError;
import com.technicaltest.payments.domain.exceptions.InvalidPaymentException;
import com.technicaltest.payments.domain.exceptions.PaymentAccountNotFoundException;
import com.technicaltest.payments.domain.values.AccountId;
import com.technicaltest.payments.domain.values.PaymentId;
import org.junit.jupiter.api.Test;

import static com.technicaltest.payments.domain.values.ErrorType.OTHER;
import static com.technicaltest.payments.domain.values.PaymentType.OFFLINE;
import static com.technicaltest.payments.domain.values.PaymentType.ONLINE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterNotValidatedPaymentUseCaseTest {

    private AccountRepository accountRepository = mock(AccountRepository.class);

    private PaymentRepository paymentRepository = mock(PaymentRepository.class);

    private PaymentGatewayRepository paymentGatewayRepository = mock(PaymentGatewayRepository.class);

    private PaymentErrorsRepository paymentErrorsRepository = mock(PaymentErrorsRepository.class);

    @Test
    void create_payment_when_already_exists_nothing_is_done() {
        // GIVEN
        var paymentCreatedOn = LocalDateTime.of(2022, 1, 4, 17, 0, 10);
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        AccountId accountId = new AccountId(1);
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .accountId(accountId)
            .paymentType(ONLINE)
            .creditCard("abcde")
            .amount(BigDecimal.ONE)
            .createdOn(paymentCreatedOn)
            .build();
        RegisterNotValidatedPaymentUseCase sut = new PaymentsService(accountRepository, paymentRepository,
            paymentGatewayRepository, paymentErrorsRepository);

        when(paymentRepository.exists(paymentId)).thenReturn(true);

        // WHEN
        sut.validateAndSavePayment(payment);

        // THEN
        verify(accountRepository, never()).update(any());
        verify(paymentRepository, never()).save(payment);
        verify(paymentErrorsRepository, never()).save(any());
    }

    @Test
    void create_payment_when_not_exists_and_invalid_payment_should_log_error() {
        // GIVEN
        var paymentCreatedOn = LocalDateTime.of(2022, 1, 4, 17, 0, 10);
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        AccountId accountId = new AccountId(1);
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .accountId(accountId)
            .paymentType(ONLINE)
            .creditCard("abcde")
            .amount(BigDecimal.ONE)
            .createdOn(paymentCreatedOn)
            .build();
        RegisterNotValidatedPaymentUseCase sut = new PaymentsService(accountRepository, paymentRepository,
            paymentGatewayRepository, paymentErrorsRepository);

        when(paymentRepository.exists(paymentId)).thenReturn(false);
        when(paymentGatewayRepository.validPayment(payment)).thenReturn(false);

        // WHEN
        sut.validateAndSavePayment(payment);

        // THEN
        verify(accountRepository, never()).update(any());
        verify(paymentRepository, never()).save(payment);
        verify(paymentErrorsRepository).save(new InvalidPaymentException(paymentId).toError());
    }

    @Test
    void create_payment_when_not_exists_and_payment_valid_and_account_not_exists_should_log_error() {
        // GIVEN
        var paymentCreatedOn = LocalDateTime.of(2022, 1, 4, 17, 0, 10);
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        AccountId accountId = new AccountId(1);
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .accountId(accountId)
            .paymentType(ONLINE)
            .creditCard("abcde")
            .amount(BigDecimal.ONE)
            .createdOn(paymentCreatedOn)
            .build();
        RegisterNotValidatedPaymentUseCase sut = new PaymentsService(accountRepository, paymentRepository,
            paymentGatewayRepository, paymentErrorsRepository);

        when(paymentRepository.exists(paymentId)).thenReturn(false);
        when(paymentGatewayRepository.validPayment(payment)).thenReturn(true);
        when(accountRepository.retrieveAndLock(accountId)).thenReturn(null);

        // WHEN
        sut.validateAndSavePayment(payment);

        // THEN
        verify(accountRepository, never()).update(any());
        verify(paymentRepository, never()).save(payment);
        verify(paymentErrorsRepository).save(new PaymentAccountNotFoundException(paymentId, accountId).toError());
    }

    @Test
    void create_payment_when_not_exists_and_payment_valid_and_account_exists_should_create_payment_and_update_account() {
        // GIVEN
        var paymentCreatedOn = LocalDateTime.of(2022, 1, 4, 17, 0, 10);
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        AccountId accountId = new AccountId(1);
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .accountId(accountId)
            .paymentType(ONLINE)
            .creditCard("abcde")
            .amount(BigDecimal.ONE)
            .createdOn(paymentCreatedOn)
            .build();
        Account account = Account.builder()
            .accountId(accountId)
            .email("mail@sample.com")
            .build();
        RegisterNotValidatedPaymentUseCase sut = new PaymentsService(accountRepository, paymentRepository,
            paymentGatewayRepository, paymentErrorsRepository);

        when(paymentRepository.exists(paymentId)).thenReturn(false);
        when(paymentGatewayRepository.validPayment(payment)).thenReturn(true);
        when(accountRepository.retrieveAndLock(accountId)).thenReturn(account);

        // WHEN
        sut.validateAndSavePayment(payment);

        // THEN
        assertEquals(paymentCreatedOn, account.getLastPaymentDate());
        verify(accountRepository).update(account);
        verify(paymentRepository).save(payment);
        verify(paymentErrorsRepository, never()).save(any());
    }

    @Test
    void create_payment_when_not_exists_and_payment_valid_and_account_exists_and_unexpected_error_should_log_error() {
        // GIVEN
        var paymentCreatedOn = LocalDateTime.of(2022, 1, 4, 17, 0, 10);
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        AccountId accountId = new AccountId(1);
        Payment payment = Payment.builder()
            .paymentId(paymentId)
            .accountId(accountId)
            .paymentType(ONLINE)
            .creditCard("abcde")
            .amount(BigDecimal.ONE)
            .createdOn(paymentCreatedOn)
            .build();
        RegisterNotValidatedPaymentUseCase sut = new PaymentsService(accountRepository, paymentRepository,
            paymentGatewayRepository, paymentErrorsRepository);

        when(paymentRepository.exists(paymentId)).thenReturn(false);
        when(paymentGatewayRepository.validPayment(payment)).thenReturn(true);
        when(accountRepository.retrieveAndLock(accountId)).thenThrow(new RuntimeException("Error"));

        // WHEN
        sut.validateAndSavePayment(payment);

        // THEN
        verify(accountRepository, never()).update(any());
        verify(paymentRepository, never()).save(payment);
        verify(paymentErrorsRepository).save(PaymentError.builder()
            .paymentId(paymentId)
            .error(OTHER)
            .description("Error")
            .build());
    }


}