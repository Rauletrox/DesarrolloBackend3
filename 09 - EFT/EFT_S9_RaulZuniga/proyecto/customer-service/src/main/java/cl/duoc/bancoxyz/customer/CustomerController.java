package cl.duoc.bancoxyz.customer;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final List<CustomerResponse> customers = List.of(
      new CustomerResponse("CUST-001", "Maria Soto", "maria.soto@bancoxyz.cl", "STANDARD"),
      new CustomerResponse("CUST-002", "Diego Perez", "diego.perez@bancoxyz.cl", "PREMIUM"));

  @GetMapping
  public List<CustomerResponse> findAll() {
    return customers;
  }

  @GetMapping("/{customerId}")
  public CustomerResponse findById(@PathVariable String customerId) {
    return customers.stream()
        .filter(customer -> customer.customerId().equals(customerId))
        .findFirst()
        .orElse(new CustomerResponse(customerId, "Cliente no encontrado", "", "UNKNOWN"));
  }

  @PostMapping
  public CustomerResponse create(@RequestBody CustomerResponse request) {
    return request;
  }

  public record CustomerResponse(String customerId, String fullName, String email, String segment) {
  }
}
