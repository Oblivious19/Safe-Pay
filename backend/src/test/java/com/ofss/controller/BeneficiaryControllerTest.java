package com.ofss.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.BeneficiaryService;

@ExtendWith(MockitoExtension.class)
class BeneficiaryControllerTest {

    private static final Long OWNER_ID = 101L;
    private static final String CORRELATION_ID =
            "beneficiary-controller-test";

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-09-14T14:00:00Z");

    @Mock
    private BeneficiaryService beneficiaryService;

    @Mock
    private Authentication authentication;

    @Mock
    private SafePayPrincipal principal;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-14T14:00:00Z"),
                ZoneOffset.UTC);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new BeneficiaryController(
                                beneficiaryService))
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void createsOwnedBeneficiaryAndReturnsLocation()
            throws Exception {

        when(beneficiaryService.createOwnedBeneficiary(
                org.mockito.ArgumentMatchers.eq(OWNER_ID),
                any(CreateBeneficiaryRequest.class)))
                .thenReturn(bankResponse());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/beneficiaries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", CORRELATION_ID)
                        .content("""
                                {
                                  "beneficiaryName": "  Demo Supplier  ",
                                  "nickname": "Office Vendor",
                                  "paymentMethod": "BANK_ACCOUNT",
                                  "bankName": "SafePay Demo Bank",
                                  "bankAccountNumber": "123456789012",
                                  "ifscCode": "abcd0123456",
                                  "relationshipLabel": "Supplier",
                                  "purposeNote": "Monthly invoice"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/beneficiaries/501"))
                .andExpect(jsonPath("$.beneficiaryId")
                        .value("501"))
                .andExpect(jsonPath(
                        "$.maskedDestinationIdentifier")
                        .value("********9012"))
                .andExpect(jsonPath("$.bankAccountNumber")
                        .doesNotExist())
                .andExpect(jsonPath("$.upiId")
                        .doesNotExist());

        ArgumentCaptor<CreateBeneficiaryRequest> captor =
                ArgumentCaptor.forClass(
                        CreateBeneficiaryRequest.class);

        verify(beneficiaryService)
                .createOwnedBeneficiary(
                        org.mockito.ArgumentMatchers.eq(OWNER_ID),
                        captor.capture());

        assertThat(captor.getValue().ifscCode())
                .isEqualTo("ABCD0123456");
    }

    @Test
    void listsOnlyAuthenticatedCustomersBeneficiaries()
            throws Exception {

        when(beneficiaryService.listOwnedBeneficiaries(
                OWNER_ID))
                .thenReturn(List.of(bankResponse()));

        mockMvc.perform(asCustomer(get(
                        "/api/v1/beneficiaries"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].beneficiaryId")
                        .value("501"));

        verify(beneficiaryService)
                .listOwnedBeneficiaries(OWNER_ID);
    }

    @Test
    void retrievesOwnedBeneficiary()
            throws Exception {

        when(beneficiaryService.getOwnedBeneficiary(
                OWNER_ID,
                501L))
                .thenReturn(bankResponse());

        mockMvc.perform(asCustomer(get(
                        "/api/v1/beneficiaries/501"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("ACTIVE"));

        verify(beneficiaryService)
                .getOwnedBeneficiary(OWNER_ID, 501L);
    }

    @Test
    void updatesOwnedBeneficiaryStatus()
            throws Exception {

        BeneficiaryResponse disabled = new BeneficiaryResponse(
                "501",
                "Demo Supplier",
                null,
                BeneficiaryPaymentMethod.BANK_ACCOUNT,
                "SafePay Demo Bank",
                "********9012",
                "ABCD0123456",
                null,
                null,
                BeneficiaryStatus.DISABLED,
                OCCURRED_AT,
                OCCURRED_AT.plusMinutes(1));

        when(beneficiaryService
                .updateOwnedBeneficiaryStatus(
                        org.mockito.ArgumentMatchers.eq(OWNER_ID),
                        org.mockito.ArgumentMatchers.eq(501L),
                        any(UpdateBeneficiaryStatusRequest.class)))
                .thenReturn(disabled);

        mockMvc.perform(asCustomer(patch(
                        "/api/v1/beneficiaries/501/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("DISABLED"));
    }

    @Test
    void rejectsInvalidPaymentDetailCombination()
            throws Exception {

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beneficiaryName": "Demo Supplier",
                                  "paymentMethod": "BANK_ACCOUNT",
                                  "bankName": "SafePay Demo Bank",
                                  "bankAccountNumber": "123456789012",
                                  "ifscCode": "ABCD0123456",
                                  "upiId": "merchant@safepay"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(beneficiaryService);
    }

    @Test
    void rejectsMalformedPaymentMethod()
            throws Exception {

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beneficiaryName": "Demo Merchant",
                                  "paymentMethod": "CARD",
                                  "upiId": "merchant@safepay"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("MALFORMED_REQUEST"));

        verifyNoInteractions(beneficiaryService);
    }

    @Test
    void returnsConflictForDuplicateBeneficiary()
            throws Exception {

        when(beneficiaryService.createOwnedBeneficiary(
                org.mockito.ArgumentMatchers.eq(OWNER_ID),
                any(CreateBeneficiaryRequest.class)))
                .thenThrow(new DuplicateResourceExcp(
                        "BENEFICIARY_ALREADY_EXISTS",
                        "Beneficiary already exists"));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/beneficiaries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpiJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("BENEFICIARY_ALREADY_EXISTS"));
    }

    @Test
    void hidesMissingOrForeignOwnedBeneficiary()
            throws Exception {

        when(beneficiaryService.getOwnedBeneficiary(
                OWNER_ID,
                501L))
                .thenThrow(new ResourceNotFoundExcp(
                        "BENEFICIARY_NOT_FOUND",
                        "Beneficiary was not found"));

        mockMvc.perform(asCustomer(get(
                        "/api/v1/beneficiaries/501"))
                        .accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("BENEFICIARY_NOT_FOUND"));
    }

    @Test
    void rejectsMissingStatusBeforeCallingService()
            throws Exception {

        mockMvc.perform(patch(
                        "/api/v1/beneficiaries/501/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(beneficiaryService);
    }

    @Test
    void failsClosedWithoutServerAuthentication()
            throws Exception {

        mockMvc.perform(get("/api/v1/beneficiaries"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(beneficiaryService);
    }

    private MockHttpServletRequestBuilder asCustomer(
            MockHttpServletRequestBuilder request) {

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal())
                .thenReturn(principal);
        when(principal.getUserId()).thenReturn(OWNER_ID);

        return request.principal(authentication);
    }

    private BeneficiaryResponse bankResponse() {
        return new BeneficiaryResponse(
                "501",
                "Demo Supplier",
                "Office Vendor",
                BeneficiaryPaymentMethod.BANK_ACCOUNT,
                "SafePay Demo Bank",
                "********9012",
                "ABCD0123456",
                "Supplier",
                "Monthly invoice",
                BeneficiaryStatus.ACTIVE,
                OCCURRED_AT,
                OCCURRED_AT);
    }

    private String validUpiJson() {
        return """
                {
                  "beneficiaryName": "Demo Merchant",
                  "paymentMethod": "UPI",
                  "upiId": "merchant@safepay"
                }
                """;
    }
}
