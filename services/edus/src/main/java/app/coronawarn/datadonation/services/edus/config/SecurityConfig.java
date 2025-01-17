package app.coronawarn.datadonation.services.edus.config;

import static app.coronawarn.datadonation.common.config.UrlConstants.GENERATE_OTP_ROUTE;
import static app.coronawarn.datadonation.common.config.UrlConstants.HEALTH_ROUTE;
import static app.coronawarn.datadonation.common.config.UrlConstants.LIVENESS_ROUTE;
import static app.coronawarn.datadonation.common.config.UrlConstants.OTP;
import static app.coronawarn.datadonation.common.config.UrlConstants.PROMETHEUS_ROUTE;
import static app.coronawarn.datadonation.common.config.UrlConstants.READINESS_ROUTE;
import static app.coronawarn.datadonation.common.config.UrlConstants.SURVEY;
import static java.util.Collections.emptyList;

import java.util.Arrays;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  /**
   * Validation factory bean is configured here because its message interpolation mechanism is considered a potential
   * threat if enabled.
   */
  @Bean
  public static LocalValidatorFactoryBean defaultValidator() {
    final LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
    factoryBean.setMessageInterpolator(new ParameterMessageInterpolator());
    return factoryBean;
  }

  @Override
  protected void configure(final HttpSecurity http) throws Exception {
    final ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry = http
        .authorizeRequests();
    registry.mvcMatchers(HttpMethod.POST, SURVEY + OTP).authenticated().and().x509()
        .userDetailsService(userDetailsService());
    registry
        .mvcMatchers(HttpMethod.GET, HEALTH_ROUTE, PROMETHEUS_ROUTE, READINESS_ROUTE, LIVENESS_ROUTE).permitAll()
        .mvcMatchers(HttpMethod.GET, GENERATE_OTP_ROUTE).permitAll();
    registry.anyRequest().denyAll().and().csrf().disable();
    http.headers().contentSecurityPolicy("default-src 'self'");
  }

  @Bean
  protected HttpFirewall strictFirewall() {
    final StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowedHttpMethods(Arrays.asList(HttpMethod.GET.name(), HttpMethod.POST.name()));
    return firewall;
  }

  @Bean
  @Override
  public UserDetailsService userDetailsService() {
    return username -> new User(username, "", emptyList());
  }
}
