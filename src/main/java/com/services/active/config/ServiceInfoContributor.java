package com.services.active.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceInfoContributor implements InfoContributor {

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    @Value("${spring.application.name:application}")
    private String appName;

    public ServiceInfoContributor(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    @Override
    public void contribute(Info.Builder builder) {
        BuildProperties buildProps = buildPropertiesProvider.getIfAvailable();
        Map<String, Object> service = new HashMap<>();
        service.put("name", appName);
        String version = null;
        if (buildProps != null) {
            version = buildProps.getVersion();
        }
        if (version == null) {
            Package pkg = ServiceInfoContributor.class.getPackage();
            version = pkg != null ? pkg.getImplementationVersion() : null;
        }
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        service.put("version", version);
        builder.withDetail("service", service);
    }
}
