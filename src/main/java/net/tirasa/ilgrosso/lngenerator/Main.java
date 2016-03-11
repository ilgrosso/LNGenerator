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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
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

    private static final String LOCAL_M2_REPO = System.getProperty("user.home").
            concat(File.separator).concat(".m2").concat(File.separator).concat("repository");

    private static final Path LOCAL_M2_REPO_PATH = Paths.get(LOCAL_M2_REPO);

    private static final M2GavCalculator GAV_CALCULATOR = new M2GavCalculator();

    private static final String[] CONSOLIDATING_GROUP_IDS = new String[] {
        "net.tirasa.connid", "org.slf4j", "org.springframework.security", "org.springframework", "io.swagger", 
        "org.activiti", "com.googlecode.wicket-jquery-ui"
    };

    private static final String[] CONSOLIDATING_GROUP_PREFIXES = new String[] {
        "com.fasterxml.jackson" };

    public static void main(final String[] args) throws IOException {
        LOG.debug("Local Maven repo is {}", LOCAL_M2_REPO);
        LOG.debug("Path argument is {}", args[0]);

        Set<String> keys = new HashSet<>();

        Files.walk(Paths.get(args[0])).
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
                                    && !gav.getGroupId().equals("xml-apis")) {

                                if (ArrayUtils.contains(CONSOLIDATING_GROUP_IDS, gav.getGroupId())) {
                                    keys.add(gav.getGroupId());
                                } else {
                                    boolean prefixFound = false;
                                    for (String prefix : CONSOLIDATING_GROUP_PREFIXES) {
                                        if (gav.getGroupId().startsWith(prefix)) {
                                            prefixFound = true;
                                            keys.add(prefix);
                                        }
                                    }

                                    if (!prefixFound) {
                                        keys.add(gav.getGroupId() + ":" + gav.getArtifactId());
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        LOG.error("While looking for Maven artifacts from the local Maven repo", e);
                    }
                });

        keys.stream().sorted().forEach(System.out::println);
    }
}
