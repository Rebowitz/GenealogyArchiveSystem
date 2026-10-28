package org.genealogyarchive.boot;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The browser-facing web application process. Scans only the web and operational packages; core
 * services come from {@link CoreConfiguration}.
 */
@SpringBootApplication(scanBasePackages = {"org.genealogyarchive.web", "org.genealogyarchive.ops"})
@Import(CoreConfiguration.class)
public class WebApplication {
}
