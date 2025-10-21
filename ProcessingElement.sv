OVERVIEW: MLIR-based FIRRTL compiler

USAGE: firtool [options] <input file>

OPTIONS:

General options:

  -O=<value>                                                 - Controls how much optimization should be performed
    =debug                                                   -   Compile with only necessary optimizations
    =release                                                 -   Compile with optimizations
  --add-mux-pragmas                                          - Annotate mux pragmas for memory array access
  --add-vivado-ram-address-conflict-synthesis-bug-workaround - Add a vivado specific SV attribute (* ram_style = "distributed" *) to unpacked array registers as a workaronud for a vivado synthesis bug that incorrectly modifies address conflict behavivor of combinational memories
  --blackbox-path=<path>                                     - Optional path to use as the root of black box annotations
  --ckg-enable=<string>                                      - Clock gate enable port name
  --ckg-input=<string>                                       - Clock gate input port name
  --ckg-name=<string>                                        - Clock gate module name
  --ckg-output=<string>                                      - Clock gate output port name
  --ckg-test-enable=<string>                                 - Clock gate test enable port name (optional)
  --disable-aggressive-merge-connections                     - Disable aggressive merge connections (i.e. merge all field-level connections into bulk connections)
  --disable-annotation-classless                             - Ignore annotations without a class when parsing
  --disable-annotation-unknown                               - Ignore unknown annotations when parsing
  --disable-layer-sink                                       - Disable layer sink
  --disable-opt                                              - Disable optimizations
  Disable random initialization code (may break semantics!)
      --disable-mem-randomization                               - Disable emission of memory randomization code
      --disable-reg-randomization                               - Disable emission of register randomization code
      --disable-all-randomization                               - Disable emission of all randomization code
  --disable-wire-elimination                                 - Disable wire elimination
  --emit-all-bind-files                                      - Emit bindfiles for private modules
  --emit-separate-always-blocks                              - Prevent always blocks from being merged and emit constructs into separate always blocks whenever possible
  --etc-disable-instance-extraction                          - Disable extracting instances only that feed test code
  --etc-disable-module-inlining                              - Disable inlining modules that only feed test code
  --etc-disable-register-extraction                          - Disable extracting registers that only feed test code
  --export-module-hierarchy                                  - Export module and instance hierarchy as JSON
  --extract-test-code                                        - Run the extract test code pass
  --fixup-eicg-wrapper                                       - Lower `EICG_wrapper` modules into clock gate intrinsics
  -g                                                         - Enable the generation of debug information
  --ignore-read-enable-mem                                   - Ignore the read enable signal, instead of assigning X on read disable
  --lint-static-asserts                                      - Lint static assertions
  --lint-xmrs-in-design                                      - Lint XMRs in the design
  --lower-memories                                           - Lower memories to have memories with masks as an array with one memory per ground type
  --mlir-disable-threading                                   - Disable multi-threading within MLIR, overrides any further call to MLIRContext::enableMultiThreading()
  --mlir-elide-elementsattrs-if-larger=<uint>                - Elide ElementsAttrs with "..." that have more elements than the given upper limit
  --mlir-elide-resource-strings-if-larger=<uint>             - Elide printing value of resources if string is too long in chars.
  --mlir-output-format=<value>                               - Output format for timing data
    =text                                                    -   display the results in text format
    =json                                                    -   display the results in JSON format
  --mlir-pass-pipeline-crash-reproducer=<string>             - Generate a .mlir reproducer file at the given output path if the pass manager crashes or fails
  --mlir-pass-pipeline-local-reproducer                      - When generating a crash reproducer, attempt to generated a reproducer with the smallest pipeline.
  --mlir-pass-statistics                                     - Display the statistics of each pass
  --mlir-pass-statistics-display=<value>                     - Display method for pass statistics
    =list                                                    -   display the results in a merged list sorted by pass name
    =pipeline                                                -   display the results with a nested pipeline view
  --mlir-pretty-debuginfo                                    - Print pretty debug info in MLIR output
  --mlir-print-debuginfo                                     - Print debug info in MLIR output
  --mlir-print-elementsattrs-with-hex-if-larger=<long>       - Print DenseElementsAttrs with a hex string that have more elements than the given upper limit (use -1 to disable)
  --mlir-print-ir-after=<pass-arg>                           - Print IR after specified passes
  --mlir-print-ir-after-all                                  - Print IR after each pass
  --mlir-print-ir-after-change                               - When printing the IR after a pass, only print if the IR changed
  --mlir-print-ir-after-failure                              - When printing the IR after a pass, only print if the pass failed
  --mlir-print-ir-before=<pass-arg>                          - Print IR before specified passes
  --mlir-print-ir-before-all                                 - Print IR before each pass
  --mlir-print-ir-module-scope                               - When printing IR for print-ir-[before|after]{-all} always print the top-level operation
  --mlir-print-ir-tree-dir=<string>                          - When printing the IR before/after a pass, print file tree rooted at this directory. Use in conjunction with mlir-print-ir-* flags
  --mlir-print-local-scope                                   - Print with local scope and inline information (eliding aliases for attributes, types, and locations)
  --mlir-print-op-on-diagnostic                              - When a diagnostic is emitted on an operation, also print the operation as an attached note
  --mlir-print-skip-regions                                  - Skip regions when printing ops.
  --mlir-print-stacktrace-on-diagnostic                      - When a diagnostic is emitted, also print the stack trace as an attached note
  --mlir-print-unique-ssa-ids                                - Print unique SSA ID numbers for values, block arguments and naming conflicts across all regions
  --mlir-print-value-users                                   - Print users of operation results and block arguments as a comment
  --mlir-timing                                              - Display execution times
  --mlir-timing-display=<value>                              - Display method for timing data
    =list                                                    -   display the results in a list sorted by total time
    =tree                                                    -   display the results ina with a nested tree view
  --mlir-use-nameloc-as-prefix                               - Print SSA IDs using NameLocs as prefixes
  --no-dedup                                                 - Disable deduplication of structurally identical modules
  -o <filename>                                              - Output filename, or directory for split output
  --output-annotation-file=<filename>                        - Optional output annotation file
  --preserve-aggregate=<value>                               - Specify input file format:
    =none                                                    -   Preserve no aggregate
    =1d-vec                                                  -   Preserve only 1d vectors of ground type
    =vec                                                     -   Preserve only vectors
    =all                                                     -   Preserve vectors and bundles
  --preserve-values=<value>                                  - Specify the values which can be optimized away
    =strip                                                   -   Strip all names. No name is preserved
    =none                                                    -   Names could be preserved by best-effort unlike `strip`
    =named                                                   -   Preserve values with meaningful names
    =all                                                     -   Preserve all values
  --repl-seq-mem                                             - Replace the seq mem for macro replacement and emit relevant metadata
  --repl-seq-mem-file=<string>                               - File name for seq mem metadata
  --select-default-for-unspecified-instance-choice           - Specialize instance choice to default, if no option selected
  --strip-debug-info                                         - Disable source locator information in output Verilog
  --strip-fir-debug-info                                     - Disable source fir locator information in output Verilog
  --symbolic-values=<value>                                  - Control how symbolic values are lowered
    =extmodule                                               -   Lower to instances of an external module
    =yosys                                                   -   Lower to `(* anyseq *)` wire declarations
  --vb-to-bv                                                 - Transform vectors of bundles to bundles of vectors
  --verification-flavor=<value>                              - Specify a verification flavor used in LowerFIRRTLToHW
    =none                                                    -   Use the flavor specified by the op
    =if-else-fatal                                           -   Use Use `if(cond) else $fatal(..)` format
    =immediate                                               -   Use immediate verif statements
    =sva                                                     -   Use SVA
  --warn-on-unprocessed-annotations                          - Warn about annotations that were not removed by lower-to-hw

Generic Options:

  --help                                                     - Display available options (--help-hidden for more)
  --help-list                                                - Display list of available options (--help-list-hidden for more)
  --version                                                  - Display the version of this program

firtool Options:

  -I                                                         - Alias for --include-dir.  Example: -I<directory>
  --annotation-file=<filename>                               - Optional input annotation file
  --default-layer-specialization=<value>                     - The default specialization for layers
    =none                                                    -   Layers are unchanged
    =disable                                                 -   Layers are disabled
    =enable                                                  -   Layers are enabled
  --disable-layers=<layer-list>                              - disable these layers permanently
  --emit-bytecode                                            - Emit bytecode when generating MLIR output
  --emit-hgldd                                               - Emit HGLDD debug info
  --enable-layers=<layer-list>                               - enable these layers permanently
  -f                                                         - Enable binary output on terminals
  --format=<value>                                           - Specify input file format:
    =autodetect                                              -   Autodetect input format
    =fir                                                     -   Parse as .fir file
    =mlir                                                    -   Parse as .mlir or .mlirbc file
  --hgldd-only-existing-file-locs                            - Only consider locations in files that exist on disk
  --hgldd-output-dir=<path>                                  - Directory into which to emit HGLDD files
  --hgldd-output-prefix=<path>                               - Prefix for output file paths in HGLDD output
  --hgldd-source-prefix=<path>                               - Prefix for source file paths in HGLDD output
  --high-firrtl-pass-plugin=<string>                         - Insert passes after parsing FIRRTL. Specify passes with MLIR textual format.
  --hw-pass-plugin=<string>                                  - Insert passes after lowering to HW. Specify passes with MLIR textual format.
  Location tracking:
      --ignore-info-locators                                    - Ignore the @info locations in the .fir file
      --fuse-info-locators                                      - @info locations are fused with .fir locations
      --prefer-info-locators                                    - Use @info locations when present, fallback to .fir locations
  --include-dir=<directory>                                  - Directory to search in when resolving source references
  --load-pass-plugin=<string>                                - Load passes from plugin library
  --low-firrtl-pass-plugin=<string>                          - Insert passes before lowering to HW. Specify passes with MLIR textual format.
  --lowering-options=<value>                                 - Style options.  Valid flags include: noAlwaysComb, exprInEventControl, disallowPackedArrays, disallowLocalVariables, verifLabels, emittedLineLength=<n>, maximumNumberOfTermsPerExpression=<n>, explicitBitcast, emitReplicatedOpsToHeader, locationInfoStyle={plain,wrapInAtSquareBracket,none}, disallowPortDeclSharing, printDebugInfo, disallowExpressionInliningInPorts, disallowMuxInlining, emitWireInPort, emitBindComments, omitVersionComment, caseInsensitiveKeywords
  --output-error-diagnostics=<filename>                      - Output error diagnostics to a JSON file
  --output-final-mlir=<filename>                             - Optional file name to output the final MLIR into, in addition to the output requested by -o
  --output-hw-mlir=<filename>                                - Optional file name to output the HW IR into, in addition to the output requested by -o
  --scalarize-ext-modules                                    - Scalarize the ports of any external modules
  --scalarize-internal-modules                               - Scalarize the ports of any internal modules
  --scalarize-public-modules                                 - Scalarize all public modules
  --select-instance-choice=<string>                          - Options to specialize instance choice, in option=case format
  Specify output format:
      --parse-only                                              - Emit FIR dialect after parsing, verification, and annotation lowering
      --ir-fir                                                  - Emit FIR dialect after pipeline
      --ir-hw                                                   - Emit HW dialect
      --ir-sv                                                   - Emit SV dialect
      --ir-verilog                                              - Emit IR after Verilog lowering
      --verilog                                                 - Emit Verilog
      --btor2                                                   - Emit BTOR2
      --split-verilog                                           - Emit Verilog (one file per module; specify directory with -o=<dir>)
      --disable-output                                          - Do not output anything
  --sv-pass-plugin=<string>                                  - Insert passes after lowering to SV. Specify passes with MLIR textual format.
  --verbose-pass-executions                                  - Log executions of toplevel module passes
  --verify-each                                              - Run the verifier after each transformation pass
