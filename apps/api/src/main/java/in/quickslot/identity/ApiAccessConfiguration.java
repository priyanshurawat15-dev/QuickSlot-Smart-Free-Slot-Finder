package in.quickslot.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiAccessConfiguration implements WebMvcConfigurer {
  private final AuthService auth;

  public ApiAccessConfiguration(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod)) return true;
                String path = request.getRequestURI();
                boolean publicRead =
                    request.getMethod().equals("GET")
                        && (path.equals("/api/health")
                            || path.equals("/api/rooms")
                            || path.equals("/api/clubs")
                            || path.matches("/api/clubs/[^/]+")
                            || path.equals("/api/availability")
                            || path.matches("/api/rooms/[^/]+/schedule"));
                boolean login =
                    request.getMethod().equals("POST") && path.equals("/api/auth/login");
                if (!publicRead && !login) auth.require(request);
                return true;
              }
            })
        .addPathPatterns("/api/**");
  }
}
