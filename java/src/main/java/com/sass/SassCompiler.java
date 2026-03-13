package com.sass;

import com.sass.exception.SassException;
import com.sass.importer.FilesystemImporter;
import com.sass.importer.ImportCache;
import com.sass.parse.ScssParser;
import com.sass.visitor.CssSerializer;
import com.sass.visitor.EvaluateVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Public API for compiling Sass/SCSS to CSS.
 *
 * <p>Usage (string compilation):</p>
 * <pre>{@code
 * CompileResult result = SassCompiler.compile("$color: red; .foo { color: $color; }");
 * System.out.println(result.css());
 * }</pre>
 *
 * <p>Usage (file compilation with @import/@use support):</p>
 * <pre>{@code
 * CompileResult result = SassCompiler.compileFile(Path.of("src/styles/main.scss"));
 * System.out.println(result.css());
 * }</pre>
 */
public final class SassCompiler {

    private SassCompiler() {}

    // -----------------------------------------------------------------------
    // String-based compilation (no file resolver)
    // -----------------------------------------------------------------------

    /**
     * Compiles an SCSS string to CSS with expanded output style.
     *
     * @param scss the SCSS source code
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     */
    public static CompileResult compile(String scss) {
        return compile(scss, OutputStyle.EXPANDED);
    }

    /**
     * Compiles an SCSS string to CSS with compressed output style.
     *
     * @param scss the SCSS source code
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     */
    public static CompileResult compileCompressed(String scss) {
        return compile(scss, OutputStyle.COMPRESSED);
    }

    /**
     * Compiles an SCSS string to CSS with the specified output style.
     *
     * @param scss the SCSS source code
     * @param style the output style (EXPANDED or COMPRESSED)
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     */
    public static CompileResult compile(String scss, OutputStyle style) {
        // 1. Parse
        var parser = new ScssParser(scss);
        var stylesheet = parser.parse();

        // 2. Evaluate
        var evaluator = new EvaluateVisitor();
        var cssTree = evaluator.evaluate(stylesheet);

        // 3. Serialize
        var css = CssSerializer.serialize(cssTree, style);

        return new CompileResult(css, null);
    }

    /**
     * Compiles an SCSS string to CSS with file-resolver support.
     *
     * <p>This overload enables @import and @use directives to resolve files
     * relative to the working directory and the specified load paths.</p>
     *
     * @param scss the SCSS source code
     * @param options compilation options (load paths, output style)
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     */
    public static CompileResult compile(String scss, CompileOptions options) {
        // 1. Parse
        var parser = new ScssParser(scss);
        var stylesheet = parser.parse();

        // 2. Evaluate with import support
        var importerInstance = new FilesystemImporter(options.loadPaths());
        var cache = new ImportCache();
        var evaluator = new EvaluateVisitor(importerInstance, cache, null);
        var cssTree = evaluator.evaluate(stylesheet);

        // 3. Serialize
        var css = CssSerializer.serialize(cssTree, options.style());

        return new CompileResult(css, null);
    }

    // -----------------------------------------------------------------------
    // File-based compilation (with file resolver)
    // -----------------------------------------------------------------------

    /**
     * Compiles an SCSS file to CSS with default options (expanded style, no extra load paths).
     *
     * <p>The file's directory is automatically used as the base for relative imports.
     * Use {@link #compileFile(Path, CompileOptions)} to specify additional load paths.</p>
     *
     * @param path the path to the SCSS file
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     * @throws IOException if the file cannot be read
     */
    public static CompileResult compileFile(Path path) throws IOException {
        return compileFile(path, new CompileOptions());
    }

    /**
     * Compiles an SCSS file to CSS with the specified options.
     *
     * <p>The file's directory is automatically used as the base for relative imports.
     * Additional directories can be specified via {@code options.loadPaths()}.</p>
     *
     * @param path the path to the SCSS file
     * @param options compilation options (load paths, output style)
     * @return the compilation result containing CSS text
     * @throws SassException if the source code has syntax or evaluation errors
     * @throws IOException if the file cannot be read
     */
    public static CompileResult compileFile(Path path, CompileOptions options) throws IOException {
        var canonicalPath = path.toAbsolutePath().normalize();
        var sourceUri = canonicalPath.toUri();
        var contents = Files.readString(canonicalPath);

        // 1. Parse
        var parser = new ScssParser(contents, sourceUri);
        var stylesheet = parser.parse();

        // 2. Evaluate with import support
        var importerInstance = new FilesystemImporter(options.loadPaths());
        var cache = new ImportCache();
        cache.putParsedStylesheet(canonicalPath, stylesheet);
        var evaluator = new EvaluateVisitor(importerInstance, cache, sourceUri);
        var cssTree = evaluator.evaluate(stylesheet);

        // 3. Serialize
        var css = CssSerializer.serialize(cssTree, options.style());

        return new CompileResult(css, null);
    }
}
