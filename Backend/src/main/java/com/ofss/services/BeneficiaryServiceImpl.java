package com.ofss.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.excp.ResourceNotFoundExcp;
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
    public Beneficiary addBeneficiary(Long accountId, Beneficiary beneficiary, String email) {
        Account account = accountDao.findByAccountIdAndUserEmail(accountId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        beneficiary.setBeneficiaryId(null);
        beneficiary.setAccount(account);
        beneficiary.setStatus("ACTIVE");
        beneficiary.setCreatedAt(LocalDateTime.now());
        return beneficiaryDao.save(beneficiary);
    }

    @Override
    public List<Beneficiary> getBeneficiaries(String email) {
        return beneficiaryDao.findByAccountUserEmail(email);
    }

    @Override
    public Beneficiary updateStatus(Long beneficiaryId, String status, String email) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        Beneficiary beneficiary = beneficiaryDao.findByBeneficiaryIdAndAccountUserEmail(beneficiaryId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));
        beneficiary.setStatus(status);
        return beneficiaryDao.save(beneficiary);
    }
}
