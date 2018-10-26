/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.modules.dump;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author jdenise@redhat.com
 */
public class ModulesDump {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new Exception("Usage: <module name> <JBOSS HOME> --include-optional\n Tool creates a directory module-<module-name>-dump-result");
        }
        String module = args[0];
        String home = args[1];
        boolean includeOptional = args.length == 3 && "--include-optional".equals(args[2]);
        Path modulePath = Paths.get(home, "modules/system/layers/base");
        if(!Files.exists(modulePath)) {
            throw new Exception("Invalid JBOSS Home");
        }
        Path aModulePath = Paths.get(home, "modules/system/layers/base/"+module.replaceAll("\\.", File.separator));
        if(!Files.exists(aModulePath)) {
            throw new Exception("Module doesn't exist");
        }
        File dir = new File("module-" + module + "-dump-result");
        if (Files.exists(dir.toPath())) {
            recursiveDelete(dir.toPath());
        }
        dir.mkdir();
        List<String> modules = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Set<String>> passives = new HashMap<>();
        Map<String, Set<String>> optionals = new HashMap<>();

        getDependencies(modulePath, module, seen, dir, "", modules, includeOptional,
                passives, optionals);
        File reversed = new File(dir, "reversed");
        reversed.mkdir();
        for (String p : modules) {
            String[] split = p.split("/");
            File current = reversed;
            for (int i = split.length - 1; i > 0; i--) {
                File f = new File(current, split[i]);
                f.mkdir();
                current = f;
            }
        }
        File passivesDir = new File(dir, "passives");
        passivesDir.mkdir();
        int numPassives = 0;
        for (Entry<String, Set<String>> p : passives.entrySet()) {
            String mod = p.getKey();
            Set<String> paths = p.getValue();
            File modDir = new File(passivesDir, mod);
            modDir.mkdir();
            for (String path : paths) {
                numPassives += 1;
                String[] split = path.split("/");
                File current = modDir;
                for (int i = split.length - 1; i > 0; i--) {
                    File f = new File(current, split[i]);
                    f.mkdir();
                    current = f;
                }
            }
        }
        File optionalsDir = new File(dir, "optionals");
        optionalsDir.mkdir();
        int numOptionals = 0;
        for (Entry<String, Set<String>> p : optionals.entrySet()) {
            String mod = p.getKey();
            Set<String> paths = p.getValue();
            File modDir = new File(optionalsDir, mod);
            modDir.mkdir();
            for (String path : paths) {
                numOptionals += 1;
                String[] split = path.split("/");
                File current = modDir;
                for (int i = split.length - 1; i > 0; i--) {
                    File f = new File(current, split[i]);
                    f.mkdir();
                    current = f;
                }
            }
        }
        System.out.println("Found " + seen.size() + " included modules");
        System.out.println("Found " + numOptionals + " optional dependencies");
        System.out.println("Found " + numPassives + " passive optional dependencies");
        System.out.println("Directory generated: " + dir);
    }

    static void getDependencies(Path modulePath, String module, Set<String> seen,
            File dir, String currentPath, List<String> paths, boolean includeOptional,
            Map<String, Set<String>> passives, Map<String, Set<String>> optionals) throws Exception {
        if (seen.contains(module)) {
            File f = new File(dir, "ALREADY-REF-" + module);
            paths.add(currentPath + "/" + module);
            f.createNewFile();
            return;
        }
        dir = new File(dir, module);
        dir.mkdir();
        seen.add(module);
        Path path = Paths.get(modulePath.toString(), module.replaceAll("\\.", File.separator),
                "main/module.xml");
        paths.add(currentPath + "/" + module);
        if (!Files.exists(path)) {
            // pseudo module
            return;
        }
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(path.toFile());
        Node n = document.getElementsByTagName("dependencies").item(0);
        if (n != null) {
            NodeList deps = n.getChildNodes();
            Set<String> seenDep = new HashSet<>();
            for (int i = 0; i < deps.getLength(); i++) {
                if (deps.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) deps.item(i);
                    String mod = element.getAttribute("name");
                    if(seenDep.contains(mod)) {
                        System.out.println("WARNING: Module " + module + " contains redundant dependency to " + mod);
                        continue;
                    }
                    seenDep.add(mod);
                    if (!element.hasAttribute("optional")) {
                        getDependencies(modulePath, mod, seen, dir, currentPath + "/" + module,
                                paths, includeOptional, passives, optionals);
                    } else {
                        NodeList properties = element.getElementsByTagName("property");
                        boolean passive = false;
                        for (int j = 0; j < properties.getLength(); j++) {
                            if (properties.item(j).getNodeType() == Node.ELEMENT_NODE) {
                                Element pelement = (Element) properties.item(j);
                                String name = pelement.getAttribute("name");
                                if (name.equals("galleon.passive")) {
                                    String val = pelement.getAttribute("value");
                                    if ("true".equals(val)) {
                                        passive = true;
                                        Set<String> pathsToPassive = passives.get(mod);
                                        if (pathsToPassive == null) {
                                            pathsToPassive = new HashSet<>();
                                            passives.put(mod, pathsToPassive);
                                        }
                                        pathsToPassive.add(currentPath + "/" + module);
                                    }
                                }
                            }
                        }
                        Set<String> pathsToOptional = optionals.get(mod);
                        if (pathsToOptional == null) {
                            pathsToOptional = new HashSet<>();
                            optionals.put(mod, pathsToOptional);
                        }
                        pathsToOptional.add(currentPath + "/" + module);
                        if (includeOptional) {
                            getDependencies(modulePath, mod, seen, dir, currentPath + "/" + module,
                                    paths, includeOptional, passives, optionals);
                        } else {
                            File f = new File(dir, (passive ? "PASSIVE-" : "") + "OPTIONAL-" + mod);
                            f.createNewFile();
                        }
                    }
                }
            }
        }
    }

    private static void recursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        try {
                            Files.delete(dir);
                        } catch (IOException ex) {
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (IOException e) {
        }
    }
}
