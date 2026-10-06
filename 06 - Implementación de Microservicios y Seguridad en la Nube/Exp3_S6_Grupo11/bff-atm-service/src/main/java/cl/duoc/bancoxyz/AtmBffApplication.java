package cl.duoc.bancoxyz;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.cloud.client.loadbalancer.LoadBalanced; import org.springframework.context.annotation.*; import org.springframework.web.client.RestClient;
@SpringBootApplication public class AtmBffApplication { public static void main(String[] args){SpringApplication.run(AtmBffApplication.class,args);}@Bean @LoadBalanced RestClient.Builder loadBalancedRestClient(){return RestClient.builder();} }
