package cl.duoc.bancoxyz.account;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

  private final List<AccountResponse> accounts = List.of(
      new AccountResponse("1001", "CUST-001", "SAVINGS", new BigDecimal("2500000"), "ACTIVE"),
      new AccountResponse("2001", "CUST-002", "CHECKING", new BigDecimal("875000"), "ACTIVE"));

  @GetMapping
  public List<AccountResponse> findAll() {
    return accounts;
  }

  @GetMapping("/{accountId}")
  public AccountResponse findById(@PathVariable String accountId) {
    return accounts.stream()
        .filter(account -> account.accountId().equals(accountId))
        .findFirst()
        .orElse(new AccountResponse(accountId, "UNKNOWN", "UNKNOWN", BigDecimal.ZERO, "NOT_FOUND"));
  }

  @PostMapping
  public AccountResponse openAccount(@RequestBody OpenAccountRequest request) {
    return new AccountResponse(request.accountId(), request.customerId(), request.type(), BigDecimal.ZERO, "ACTIVE");
  }

  public record OpenAccountRequest(String accountId, String customerId, String type) {
  }

  public record AccountResponse(String accountId, String customerId, String type, BigDecimal balance, String status) {
  }
}
