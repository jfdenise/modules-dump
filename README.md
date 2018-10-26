# modules-dump
A tool to create a filesystem layout to navigate/find module dependencies tree and optional dependencies.

java -jar target/modules-dump.jar \<module name\> \<JBOSS HOME\> [--include-optional]

By default optional dependencies are identified but not included. Specify --include-optional to include them.

This will create a directory named module-<module-name>-dump-result containing 4 directories:

- optionals: Contains a directory per optional (passive or not) dependency. 
Children are paths to this optional dependency. You can navigate from each children to the root module.

- \<module name\>: Contains all the dependencies of this module. You can navigate the dependencies.
Optionals if skip, are marked as a file ([PASSIVE]-OPTIONAL-xxx). Modules already referenced by other modules are marked as
a file (ALREADY-REF-xxx).

- passives: Contains a directory per passive optional dependency. Children are paths 
to this optional dependency. You can navigate from each children to the root module.

- reversed: Contains a directory by module reachable from the root module you provided as argument. 
Children are modules that depend on this module. You can navigate from each children to the root module.

# Some interesting usages
Because the output is a directory tree, you can use find|grep commands or a GUI file explorer to analyze output content.