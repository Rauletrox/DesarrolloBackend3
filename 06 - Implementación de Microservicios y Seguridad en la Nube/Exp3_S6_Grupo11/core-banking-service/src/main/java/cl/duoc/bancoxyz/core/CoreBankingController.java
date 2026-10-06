package cl.duoc.bancoxyz.core;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/core/accounts")
public class CoreBankingController {
  private final BankingService service;
  public CoreBankingController(BankingService service) { this.service = service; }
  @GetMapping("/{id}") public BankingService.Account account(@PathVariable Integer id) { return service.account(id); }
  @GetMapping("/{id}/annual-statement") public BankingService.WebStatement statement(@PathVariable Integer id) { return service.annualStatement(id); }
  @GetMapping("/{id}/movements/latest") public List<BankingService.Movement> latest(@PathVariable Integer id) { return service.latestMovements(id); }
  @GetMapping("/{id}/balance") public Balance balance(@PathVariable Integer id) { return new Balance(id, service.account(id).finalBalance()); }
  @PostMapping("/{id}/withdraw") public BankingService.Withdrawal withdraw(@PathVariable Integer id, @RequestBody WithdrawRequest request) { return service.withdraw(id, request.amount()); }
  public record Balance(Integer accountId, BigDecimal balance) {}
  public record WithdrawRequest(BigDecimal amount) {}
}
