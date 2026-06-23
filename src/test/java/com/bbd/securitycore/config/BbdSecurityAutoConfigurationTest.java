package com.bbd.securitycore.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BbdSecurityAutoConfigurationTest {

    @Test
    void appliesTimeoutRequestFactoryOnlyToUserServiceHttpGroup() {
        BbdSecurityProperties properties = new BbdSecurityProperties();
        properties.getUserService().setConnectTimeout(Duration.ofMillis(1500));
        properties.getUserService().setReadTimeout(Duration.ofMillis(2500));
        RecordingRequestFactoryBuilder requestFactoryBuilder = new RecordingRequestFactoryBuilder();

        RestClientHttpServiceGroupConfigurer configurer =
                new BbdSecurityAutoConfiguration().bbdAccessTokenRelayHttpServiceGroupConfigurer(
                        properties,
                        objectProvider((ClientHttpRequestFactoryBuilder<?>) requestFactoryBuilder),
                        objectProvider(HttpClientSettings.defaults())
                );

        configurer.configureGroups(new RecordingGroups(
                BbdSecurityAutoConfiguration.USER_SERVICE_GROUP,
                "other-service"
        ));

        assertEquals(1, requestFactoryBuilder.recordedSettings.size());
        HttpClientSettings settings = requestFactoryBuilder.recordedSettings.getFirst();
        assertEquals(Duration.ofMillis(1500), settings.connectTimeout());
        assertEquals(Duration.ofMillis(2500), settings.readTimeout());
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfAvailable(Supplier<T> defaultSupplier) {
                return value == null ? defaultSupplier.get() : value;
            }
        };
    }

    private static class RecordingRequestFactoryBuilder implements ClientHttpRequestFactoryBuilder<ClientHttpRequestFactory> {

        private final List<HttpClientSettings> recordedSettings = new ArrayList<>();

        @Override
        public ClientHttpRequestFactory build(HttpClientSettings settings) {
            recordedSettings.add(settings);
            return (uri, httpMethod) -> {
                throw new UnsupportedOperationException("not used");
            };
        }
    }

    private static class RecordingGroups implements HttpServiceGroupConfigurer.Groups<RestClient.Builder> {

        private final List<HttpServiceGroup> groups;

        private RecordingGroups(String... groupNames) {
            this(Arrays.stream(groupNames)
                    .map(RecordingHttpServiceGroup::new)
                    .map(HttpServiceGroup.class::cast)
                    .toList());
        }

        private RecordingGroups(List<HttpServiceGroup> groups) {
            this.groups = groups;
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filterByName(String... names) {
            Set<String> acceptedNames = Set.of(names);
            return filter(group -> acceptedNames.contains(group.name()));
        }

        @Override
        public HttpServiceGroupConfigurer.Groups<RestClient.Builder> filter(Predicate<HttpServiceGroup> predicate) {
            return new RecordingGroups(groups.stream()
                    .filter(predicate)
                    .toList());
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.ClientCallback<RestClient.Builder> callback) {
            groups.forEach(group -> callback.withClient(group, RestClient.builder()));
        }

        @Override
        public void forEachClient(HttpServiceGroupConfigurer.InitializingClientCallback<RestClient.Builder> callback) {
            groups.forEach(callback::initClient);
        }

        @Override
        public void forEachProxyFactory(HttpServiceGroupConfigurer.ProxyFactoryCallback callback) {
        }

        @Override
        public void forEachGroup(HttpServiceGroupConfigurer.GroupCallback<RestClient.Builder> callback) {
            groups.forEach(group -> callback.withGroup(
                    group,
                    RestClient.builder(),
                    HttpServiceProxyFactory.builder()
            ));
        }
    }

    private record RecordingHttpServiceGroup(String name) implements HttpServiceGroup {

        @Override
        public Set<Class<?>> httpServiceTypes() {
            return Set.of();
        }

        @Override
        public ClientType clientType() {
            return ClientType.REST_CLIENT;
        }
    }
}
