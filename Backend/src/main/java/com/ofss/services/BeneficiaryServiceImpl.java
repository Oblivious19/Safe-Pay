package com.ofss.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryRequest;
import com.ofss.beans.BeneficiaryResponse;
import com.ofss.excp.DuplicateBeneficiaryException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;

@Service
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryDao beneficiaryDao;
    private final AccountDao accountDao;

    public BeneficiaryServiceImpl(BeneficiaryDao beneficiaryDao, AccountDao accountDao) {
        this.beneficiaryDao = beneficiaryDao;
        this.accountDao = accountDao;
    }

    @Override
    @Transactional
    public BeneficiaryResponse addBeneficiary(Long callerId, BeneficiaryRequest request) {
        Account account = ownedAccount(callerId, request.accountId());
        if (beneficiaryDao.countDuplicate(account.getAccountId(), request.bankAccountNumber(), request.ifsc()) > 0) {
            throw new DuplicateBeneficiaryException();
        }
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setBeneficiaryName(request.beneficiaryName());
        beneficiary.setBankAccountNumber(request.bankAccountNumber());
        beneficiary.setIfsc(request.ifsc());
        beneficiary.setAccount(account);
        beneficiary.setStatus("ACTIVE");
        beneficiary.setCreatedAt(LocalDateTime.now());
        // Flush here so racing duplicates reach the controller's safe 409 mapping.
        return BeneficiaryResponse.from(beneficiaryDao.saveAndFlush(beneficiary));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(Long callerId) {
        return beneficiaryDao.findByAccountUserUserIdAndStatusOrderByBeneficiaryId(callerId, "ACTIVE")
                .stream().map(BeneficiaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(Long callerId, boolean includeInactive) {
        if (!includeInactive) return getBeneficiaries(callerId);
        return beneficiaryDao.findByAccountUserUserIdOrderByBeneficiaryId(callerId)
                .stream().map(BeneficiaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(Long callerId, Long accountId, boolean includeInactive) {
        if (accountId == null) return getBeneficiaries(callerId, includeInactive);
        ownedAccount(callerId, accountId);
        List<Beneficiary> found = includeInactive
                ? beneficiaryDao.findByAccountAccountIdAndAccountUserUserIdOrderByBeneficiaryId(accountId, callerId)
                : beneficiaryDao.findByAccountAccountIdAndAccountUserUserIdAndStatusOrderByBeneficiaryId(accountId, callerId, "ACTIVE");
        return found.stream().map(BeneficiaryResponse::from).toList();
    }

    private Account ownedAccount(Long callerId, Long accountId) {
        if (accountId != null && accountId <= 0) throw new IllegalArgumentException("Account ID must be positive");
        return (accountId == null ? accountDao.findFirstByUserUserIdOrderByAccountId(callerId)
                : accountDao.findByAccountIdAndUserUserId(accountId, callerId))
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse getBeneficiary(Long callerId, Long beneficiaryId) {
        return beneficiaryDao.findByBeneficiaryIdAndAccountUserUserId(beneficiaryId, callerId)
                .map(BeneficiaryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));
    }

    @Override
    @Transactional
    public BeneficiaryResponse changeStatus(Long callerId, Long beneficiaryId, String target) {
        if (!"ACTIVE".equals(target) && !"INACTIVE".equals(target)) {
            throw new IllegalArgumentException("Beneficiary status must be ACTIVE or INACTIVE");
        }
        Beneficiary beneficiary = beneficiaryDao.findByBeneficiaryIdAndAccountUserUserId(beneficiaryId, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));
        String current = beneficiary.getStatus();
        if (!"ACTIVE".equals(current) && !"INACTIVE".equals(current)) {
            throw new TransactionValidationException(409, "This beneficiary status cannot be changed");
        }
        if (current.equals(target)) return BeneficiaryResponse.from(beneficiary);
        if (beneficiaryDao.changeOwnedStatus(beneficiaryId, callerId, current, target) != 1) {
            throw new TransactionValidationException(409, "Beneficiary changed concurrently; refresh and retry");
        }
        return getBeneficiary(callerId, beneficiaryId);
    }

    @Override
    @Transactional
    public void deleteBeneficiary(Long callerId, Long beneficiaryId) {
        if (beneficiaryDao.deactivateOwned(beneficiaryId, callerId) != 1) {
            throw new ResourceNotFoundExcp("Beneficiary not found");
        }
    }
}
