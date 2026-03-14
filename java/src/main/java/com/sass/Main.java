package com.sass;

import com.sass.exception.SassException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for the Sass compiler.
 *
 * <p>Usage:</p>
 * <pre>
 *   java -jar sass-compiler.jar [options] &lt;input.scss&gt; [output.css]
 *
 *   Options:
 *     --style=expanded|compressed   Output style (default: expanded)
 *     --load-path=&lt;dir&gt;             Add a directory to the import lookup list (repeatable)
 *     --stdin                        Read SCSS from standard input instead of a file
 *     --watch                        Watch for changes and recompile (not yet implemented)
 *     --help                         Show this help message
 *     --version                      Show version information
 * </pre>
 */
public final class Main {

    private static final String VERSION = "0.1.0-SNAPSHOT";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (SassException e) {
            // Rich error output with file, line, column, and source context
            var span = e.getSpan();
            if (span != null && span.sourceUrl() != null) {
                // Format: file:line:col
                var url = span.sourceUrl();
                String location;
                try {
                    location = Path.of(url).toString();
                } catch (Exception ignored) {
                    location = url.toString();
                }
                System.err.println("Error: " + e.getMessage());
                System.err.println("  " + location + ":" + (span.start().line() + 1) + ":" + (span.start().column() + 1));
                System.err.println(span.highlight());
            } else if (span != null) {
                System.err.println("Error: " + e.getMessage());
                System.err.println("  line " + (span.start().line() + 1) + ", column " + (span.start().column() + 1));
                System.err.println(span.highlight());
            } else {
                System.err.println("Error: " + e.getMessage());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        // Parse options
        OutputStyle style = OutputStyle.EXPANDED;
        List<Path> loadPaths = new ArrayList<>();
        boolean fromStdin = false;
        String inputPath = null;
        String outputPath = null;

        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                System.exit(0);
            } else if (arg.equals("--version")) {
                System.out.println("sass-compiler " + VERSION);
                System.exit(0);
            } else if (arg.equals("--stdin")) {
                fromStdin = true;
            } else if (arg.startsWith("--style=")) {
                String value = arg.substring("--style=".length());
                style = switch (value.toLowerCase()) {
                    case "expanded" -> OutputStyle.EXPANDED;
                    case "compressed" -> OutputStyle.COMPRESSED;
                    default -> throw new IllegalArgumentException(
                            "Unknown output style: " + value + ". Use 'expanded' or 'compressed'.");
                };
            } else if (arg.startsWith("--load-path=")) {
                String dir = arg.substring("--load-path=".length());
                Path p = Path.of(dir);
                if (!Files.isDirectory(p)) {
                    throw new IllegalArgumentException("Load path is not a directory: " + dir);
                }
                loadPaths.add(p);
            } else if (arg.startsWith("-I")) {
                // Short form: -I<dir> (compatible with dart-sass)
                String dir = arg.substring(2);
                Path p = Path.of(dir);
                if (!Files.isDirectory(p)) {
                    throw new IllegalArgumentException("Load path is not a directory: " + dir);
                }
                loadPaths.add(p);
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else if (inputPath == null) {
                inputPath = arg;
            } else if (outputPath == null) {
                outputPath = arg;
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
        }

        // Compile
        CompileResult result;
        var options = new CompileOptions(loadPaths, style);

        if (fromStdin) {
            String scss = new String(System.in.readAllBytes());
            result = SassCompiler.compile(scss, options);
        } else {
            if (inputPath == null) {
                System.err.println("Error: No input file specified. Use --stdin to read from stdin.");
                printUsage();
                System.exit(1);
                return;
            }
            Path input = Path.of(inputPath);
            if (!Files.isRegularFile(input)) {
                throw new IllegalArgumentException("File not found: " + inputPath);
            }
            result = SassCompiler.compileFile(input, options);
        }

        // Output
        if (outputPath != null) {
            Path output = Path.of(outputPath);
            // Create parent directories if needed
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, result.css());
        } else {
            System.out.print(result.css());
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage: sass-compiler [options] <input.scss> [output.css]

                Compiles SCSS to CSS. If no output file is given, writes to stdout.

                Options:
                  --style=expanded|compressed   Output style (default: expanded)
                  --load-path=<dir>             Add import lookup directory (repeatable)
                  -I<dir>                       Short form of --load-path
                  --stdin                       Read SCSS from stdin instead of a file
                  --help, -h                    Show this help message
                  --version                     Show version information

                Examples:
                  sass-compiler style.scss                          Compile to stdout
                  sass-compiler style.scss dist/style.css           Compile to file
                  sass-compiler --style=compressed style.scss       Compressed output
                  sass-compiler --load-path=vendor style.scss       With extra load path
                  sass-compiler -Ivendor -Ilib style.scss           Multiple load paths
                  echo ".a { color: red; }" | sass-compiler --stdin Compile from stdin
                """);
    }
}
