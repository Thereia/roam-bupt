package com.roambupt.web.global;

import com.roambupt.core.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** XFF 信任边界：默认不信，开了也只信可信代理。 */
class ClientIpResolverTest {

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.5");
        return request;
    }

    @Test
    void 默认不信任XFF_用真实来源() {
        ClientIpResolver resolver = new ClientIpResolver(new SecurityProperties(false, List.of(), 30));
        assertThat(resolver.resolve(request())).isEqualTo("10.0.0.5");
    }

    @Test
    void 开启信任且未限制代理_取最左侧客户端地址() {
        ClientIpResolver resolver = new ClientIpResolver(new SecurityProperties(true, List.of(), 30));
        assertThat(resolver.resolve(request())).isEqualTo("1.2.3.4");
    }

    @Test
    void 开启信任但来源不在可信代理列表_仍然不采信() {
        ClientIpResolver resolver = new ClientIpResolver(
                new SecurityProperties(true, List.of("10.9.9.9"), 30));
        assertThat(resolver.resolve(request())).isEqualTo("10.0.0.5");
    }

    @Test
    void 来源在可信代理列表_采信XFF() {
        ClientIpResolver resolver = new ClientIpResolver(
                new SecurityProperties(true, List.of("10.0.0.5"), 30));
        assertThat(resolver.resolve(request())).isEqualTo("1.2.3.4");
    }
}
