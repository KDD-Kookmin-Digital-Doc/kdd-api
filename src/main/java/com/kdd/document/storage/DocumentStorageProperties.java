package com.kdd.document.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.document-storage")
@Getter
@Setter
public class DocumentStorageProperties {

    private String uploadDir = "uploads";
}
