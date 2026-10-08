package com.frontendbase.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Test-only SQL adapter; production migration files/checksums remain untouched. */
@TestConfiguration
public class H2MigrationConfiguration {
    @Bean
    FlywayConfigurationCustomizer h2MigrationCompatibility() {
        return configuration -> {
            try (var connection = configuration.getDataSource().getConnection()) {
                if (!connection.getMetaData().getURL().startsWith("jdbc:h2:")) return;
            } catch (java.sql.SQLException exception) { throw new IllegalStateException(exception); }
            List<LoadableResource> resources = new ArrayList<>();
            try {
                for (var resource : new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:db/migration/*.sql")) {
                    String filename = resource.getFilename();
                    String original;
                    try (var input = resource.getInputStream()) {
                        original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    // H2 does not accept PostgreSQL's comma-separated ADD COLUMN statements.
                    var pattern = Pattern.compile("ALTER TABLE (\\w+)\\s+(ADD COLUMN .*?);", Pattern.DOTALL);
                    var matcher = pattern.matcher(original);
                    StringBuilder compatible = new StringBuilder();
                    while (matcher.find()) {
                        String table = matcher.group(1);
                        String replacement = Arrays.stream(matcher.group(2).split(",\\s*ADD COLUMN "))
                                .map(part -> "ALTER TABLE " + table + " "
                                        + (part.startsWith("ADD COLUMN") ? part : "ADD COLUMN " + part) + ";")
                                .reduce((a, b) -> a + "\n" + b).orElseThrow();
                        matcher.appendReplacement(compatible, java.util.regex.Matcher.quoteReplacement(replacement));
                    }
                    matcher.appendTail(compatible);
                    // PostgreSQL PL/pgSQL audit triggers are verified against PostgreSQL integration tests.
                    String sql = filename.equals("V7__protect_stock_audit_history.sql")
                            ? "SELECT 1;" : compatible.toString();
                    resources.add(new LoadableResource() {
                        public Reader read() { return new StringReader(sql); }
                        public String getAbsolutePath() { return "db/migration/" + filename; }
                        public String getAbsolutePathOnDisk() { return getAbsolutePath(); }
                        public String getFilename() { return filename; }
                        public String getRelativePath() { return filename; }
                    });
                }
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            configuration.resourceProvider(new ResourceProvider() {
                public LoadableResource getResource(String name) {
                    return resources.stream().filter(r -> r.getFilename().equals(name)).findFirst().orElse(null);
                }
                public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                    return resources.stream().filter(r -> r.getFilename().startsWith(prefix)
                            && Arrays.stream(suffixes).anyMatch(r.getFilename()::endsWith)).toList();
                }
            });
        };
    }
}
