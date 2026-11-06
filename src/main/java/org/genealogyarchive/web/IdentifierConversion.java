package org.genealogyarchive.web;

import org.genealogyarchive.preservation.FileIdentifiers;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * Request parameters and path variables of type UUID are converted by {@link FileIdentifiers#uuid}, the
 * same definition Change History authorization uses to recognise File references (GAS-P2-FV03). It
 * accepts exactly what Spring's default conversion accepted (blank is absent; otherwise
 * {@code UUID.fromString} of the trimmed text), so no URL that resolved before stops resolving.
 */
@Configuration(proxyBeanMethods = false)
public class IdentifierConversion implements WebMvcConfigurer {

    /** The String-to-UUID conversion used for every request parameter and path variable. */
    public static final Converter<String, UUID> STRING_TO_UUID = source -> {
        if (source == null || source.isBlank()) {
            return null;
        }
        return FileIdentifiers.uuid(source).orElseThrow(() -> new IllegalArgumentException("Invalid UUID string"));
    };

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, UUID.class, STRING_TO_UUID);
    }
}
