package com.ofss.repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.Account;
import com.ofss.beans.TransactionState;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountFundsRepository extends Repository<Account, Long> {
    interface Funds { Long getAccountId(); BigDecimal getBalance(); BigDecimal getReservedBalance(); }
    @Query("select a.accountId as accountId, a.balance as balance, coalesce(sum(t.amount),0) as reservedBalance "
            + "from Account a left join TransactionDb t on t.fromAccount = a and t.state in :states "
            + "where a.accountId = :id and a.user.userId = :owner group by a.accountId, a.balance")
    Optional<Funds> funds(@Param("id") Long id, @Param("owner") Long owner, @Param("states") List<TransactionState> states);
}
