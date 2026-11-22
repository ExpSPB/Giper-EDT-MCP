Walk the selected project's metadata from one top-level object. Use `direction=out` for dependencies, `direction=in` for dependents, or `direction=both` to explore both directions from each visited vertex.

For example, call with `projectName`, `objectFqn: "Catalog.Products"`, `direction: "in"`, and `depth: 2`. The same root can be addressed as `Справочник.Products`. Use the programmatic object name.

The JSON result contains canonical FQNs and distances in `nodes`, directed links in `edges`, and strongly connected groups in `cycles`. Each link goes from the referring object to its target even during an incoming walk. Links of the same kind (`via`) between the same pair share a row. The `count` counts distinct reference occurrences, including references held by separate attributes. A cycle can contain one node with an explicit self-reference.

The budget is 45 seconds, including preparation. If a limit stops the work or part of the model could not be read, `partial` is true and `partialReasons` describes why. Requested depth defines the scope. The cycle list describes the returned graph. Use `impact_analysis` for a direct usage report including indexed BSL.
