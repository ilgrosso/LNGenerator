/**
 * Copyright (C) 2016 Tirasa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.ilgrosso.lngenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.artifact.M2GavCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static enum LICENSE {
        PUBLIC_DOMAIN("Public Domain"),
        BSD("BSD license"),
        CDDL("CDDL 1.0"),
        EPL("EPL 1.0"),
        MIT("MIT license"),
        CPL("CPL"),
        INDIANA("Indiana University Extreme! Lab Software License, version 1.1.1"),
        ZLIB("zlib/libpng license");

        private final String label;

        LICENSE(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

    }

    private static final String LOCAL_M2_REPO = System.getProperty("user.home").
            concat(File.separator).concat(".m2").concat(File.separator).concat("repository");

    private static final Path LOCAL_M2_REPO_PATH = Paths.get(LOCAL_M2_REPO);

    private static final M2GavCalculator GAV_CALCULATOR = new M2GavCalculator();

    private static final String[] CONSOLIDATING_GROUP_IDS = new String[] {
        "net.tirasa.connid", "org.slf4j", "org.springframework.security", "org.springframework", "io.swagger",
        "org.flowable", "com.googlecode.wicket-jquery-ui", "com.sun.xml.bind", "io.dropwizard.metrics",
        "org.codehaus.izpack", "org.codehaus.plexus", "org.opensaml", "net.shibboleth.utilities", "com.google.guava"
    };

    public static void main(final String[] args) throws IOException, URISyntaxException {
        assert args.length > 0 : "No arguments provided";

        File source = new File(args[0]);
        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + args[0]);
        }

        String destPath = args.length > 1 ? args[1] : System.getProperty("java.io.tmpdir");
        File dest = new File(destPath);
        if (!dest.isDirectory() || !dest.canWrite()) {
            throw new IllegalArgumentException("Not a directory, or not writable: " + destPath);
        }

        LOG.debug("Local Maven repo is {}", LOCAL_M2_REPO);
        LOG.debug("Source Path is {}", source.getAbsolutePath());
        LOG.debug("Destination Path is {}", dest.getAbsolutePath());
        LOG.warn("Existing LICENSE and NOTICE files in {} will be overwritten!", dest.getAbsolutePath());

        Set<String> keys = new HashSet<>();

        Files.walk(source.toPath()).
                filter(Files::isRegularFile).
                map((final Path path) -> path.getFileName().toString()).
                filter((String path) -> path.endsWith(".jar")).
                sorted().
                forEach((filename) -> {
                    try (Stream<Path> stream = Files.find(LOCAL_M2_REPO_PATH, 10,
                            (path, attr) -> String.valueOf(path.getFileName().toString()).equals(filename))) {

                        String fullPath = stream.sorted().map(String::valueOf).collect(Collectors.joining("; "));
                        if (fullPath.isEmpty()) {
                            LOG.warn("Could not find {} in the local Maven repo", filename);
                        } else {
                            String path = fullPath.substring(LOCAL_M2_REPO.length() + 1);
                            Gav gav = GAV_CALCULATOR.pathToGav(path);
                            if (gav == null) {
                                LOG.error("Invalid Maven path: {}", path);
                            } else if (!gav.getGroupId().startsWith("org.apache.")
                                    && !gav.getGroupId().startsWith("commons-")
                                    && !gav.getGroupId().equals("org.codehaus.groovy")
                                    && !gav.getGroupId().equals("jakarta-regexp")
                                    && !gav.getGroupId().equals("bsf")
                                    && !gav.getGroupId().equals("xml-apis")
                                    && !gav.getGroupId().equals("xml-resolver")
                                    && !gav.getGroupId().equals("batik")) {

                                if (ArrayUtils.contains(CONSOLIDATING_GROUP_IDS, gav.getGroupId())) {
                                    keys.add(gav.getGroupId());
                                } else if (gav.getGroupId().startsWith("net.tirasa.connid")) {
                                    keys.add("net.tirasa.connid");
                                } else if (gav.getGroupId().startsWith("com.fasterxml.jackson")) {
                                    keys.add("com.fasterxml.jackson");
                                } else if (gav.getGroupId().startsWith("com.zaxxer")
                                        && gav.getArtifactId().startsWith("HikariCP")) {

                                    keys.add("com.zaxxer.HikariCP");
                                } else if (gav.getGroupId().startsWith("javax.xml.bind")) {
                                    keys.add("com.sun.xml.bind");
                                } else if (gav.getGroupId().startsWith("io.swagger.core.v3")) {
                                    keys.add("io.swagger");
                                } else if (gav.getGroupId().startsWith("com.fasterxml.woodstox")
                                        && gav.getArtifactId().startsWith("woodstox-core")) {

                                    keys.add("org.codehaus.woodstox:woodstox-core-asl");
                                } else if (gav.getGroupId().equals("org.webjars.bower")
                                        && (gav.getArtifactId().startsWith("angular-animate")
                                        || gav.getArtifactId().startsWith("angular-cookies")
                                        || gav.getArtifactId().startsWith("angular-resource")
                                        || gav.getArtifactId().startsWith("angular-sanitize")
                                        || gav.getArtifactId().startsWith("angular-aria")
                                        || gav.getArtifactId().startsWith("angular-treasure-overlay-spinner"))) {

                                    keys.add("org.webjars.bower:angular");
                                } else if (gav.getGroupId().startsWith("javax.servlet.jstl")) {
                                    keys.add("javax.servlet:jstl");
                                } else if (gav.getGroupId().equals("org.webjars.bower")
                                        && gav.getArtifactId().startsWith("angular-translate")) {

                                    keys.add("org.webjars.bower:angular-translate");
                                } else if (gav.getGroupId().startsWith("de.agilecoders")) {
                                    keys.add("wicket-bootstrap");
                                } else if (gav.getGroupId().equals("com.google.javascript")
                                        && gav.getArtifactId().startsWith("closure-compiler-")) {

                                    keys.add("com.google.javascript:closure-compiler");
                                } else if ("org.webjars".equals(gav.getGroupId())) {
                                    if (gav.getArtifactId().startsWith("jquery-ui")) {
                                        keys.add("jquery-ui");
                                    } else  if (gav.getArtifactId().startsWith("swagger-ui")) {
                                        keys.add("io.swagger");
                                    } else {
                                        keys.add(gav.getArtifactId());
                                    }
                                } else {
                                    keys.add(gav.getGroupId() + ":" + gav.getArtifactId());
                                }
                            }
                        }
                    } catch (IOException e) {
                        LOG.error("While looking for Maven artifacts from the local Maven repo", e);
                    }
                });

        final Properties licenses = new Properties();
        licenses.loadFromXML(Main.class.getResourceAsStream("/licenses.xml"));

        final Properties notices = new Properties();
        notices.loadFromXML(Main.class.getResourceAsStream("/notices.xml"));

        BufferedWriter licenseWriter = Files.newBufferedWriter(
                new File(dest, "LICENSE").toPath(), StandardOpenOption.CREATE);
        licenseWriter.write(new String(Files.readAllBytes(
                Paths.get(Main.class.getResource("/LICENSE.template").toURI()))));

        BufferedWriter noticeWriter = Files.newBufferedWriter(
                new File(dest, "NOTICE").toPath(), StandardOpenOption.CREATE);
        noticeWriter.write(new String(Files.readAllBytes(
                Paths.get(Main.class.getResource("/NOTICE.template").toURI()))));

        EnumSet<LICENSE> outputLicenses = EnumSet.noneOf(LICENSE.class);

        keys.stream().sorted().forEach((final String dependency) -> {
            if (licenses.getProperty(dependency) == null) {
                LOG.error("Could not find license information about {}", dependency);
            } else {
                try {
                    licenseWriter.write("\n==\n\nFor " + licenses.getProperty(dependency) + ":\n");

                    String depLicense = licenses.getProperty(dependency + ".license");
                    if (depLicense == null) {
                        licenseWriter.write("This is licensed under the AL 2.0, see above.");
                    } else {
                        LICENSE license = LICENSE.valueOf(depLicense);

                        if (license == LICENSE.PUBLIC_DOMAIN) {
                            licenseWriter.write("This is " + license.getLabel() + ".");
                        } else {
                            licenseWriter.write("This is licensed under the " + license.getLabel());

                            if (outputLicenses.contains(license)) {
                                licenseWriter.write(", see above.");
                            } else {
                                outputLicenses.add(license);

                                licenseWriter.write(":\n\n");
                                licenseWriter.write(new String(Files.readAllBytes(
                                        Paths.get(Main.class.getResource("/LICENSE." + license.name()).toURI()))));
                            }
                        }
                    }
                    licenseWriter.write('\n');

                    if (notices.getProperty(dependency) != null) {
                        noticeWriter.write("\n==\n\n" + notices.getProperty(dependency) + "\n");
                    }
                } catch (Exception e) {
                    LOG.error("While dealing with {}", dependency, e);
                }
            }
        });

        licenseWriter.close();
        noticeWriter.close();

        LOG.debug("Execution completed successfully, look at {} for the generated LICENSE and NOTICE files",
                dest.getAbsolutePath());
    }
}
