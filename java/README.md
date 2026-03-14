# Sass Compiler for Java

A pure-Java implementation of the [Sass](https://sass-lang.com/) CSS preprocessor, ported from [dart-sass](https://github.com/sass/dart-sass). Compiles SCSS to CSS with no native dependencies — runs anywhere Java runs.

**Status:** 0.1.0-SNAPSHOT. Produces output within 1% of dart-sass v1.98 on real-world stylesheets (tested against an 18-module, 13K-line production SCSS codebase).

## Requirements

- Java 21+
- Maven 3.8+

## Building

```bash
cd java
mvn clean install
```

This produces `target/sass-compiler-0.1.0-SNAPSHOT.jar` — a self-contained executable jar.

Run tests only:

```bash
mvn test
```

## Command-Line Usage

```
java -jar sass-compiler-0.1.0-SNAPSHOT.jar [options] <input.scss> [output.css]
```

**Always specify the output file** to get correct UTF-8 output. Do **not** use shell redirection (`>`) — on Windows/PowerShell this produces UTF-16 files that are double the expected size.

```bash
# Compile to file (recommended)
java -jar sass-compiler.jar styles.scss dist/styles.css

# Compressed output
java -jar sass-compiler.jar --style=compressed styles.scss dist/styles.min.css

# With additional import directories
java -jar sass-compiler.jar --load-path=vendor --load-path=lib styles.scss dist/styles.css
java -jar sass-compiler.jar -Ivendor -Ilib styles.scss dist/styles.css

# Compile from stdin (writes to stdout)
echo ".a { color: red; }" | java -jar sass-compiler.jar --stdin
```

### All options

| Option | Description |
|---|---|
| `--style=expanded\|compressed` | Output style (default: `expanded`) |
| `--load-path=<dir>` | Add a directory to the import lookup list (repeatable) |
| `-I<dir>` | Short form of `--load-path` |
| `--stdin` | Read SCSS from standard input instead of a file |
| `--help`, `-h` | Show help message |
| `--version` | Show version |

## Java API

### Maven Dependency

Once installed to your local repository (`mvn install`), add this to your `pom.xml`:

```xml
<dependency>
    <groupId>com.sass</groupId>
    <artifactId>sass-compiler</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Compile an SCSS string

```java
import com.sass.SassCompiler;
import com.sass.CompileResult;

CompileResult result = SassCompiler.compile("""
    $primary: #3498db;

    .button {
        background: $primary;
        &:hover {
            background: darken($primary, 10%);
        }
    }
    """);

System.out.println(result.css());
```

### Compile an SCSS file (with @use/@import support)

```java
import com.sass.SassCompiler;
import java.nio.file.Path;

CompileResult result = SassCompiler.compileFile(Path.of("src/styles/main.scss"));
System.out.println(result.css());
```

The file's directory is automatically used as the base for resolving `@use`, `@forward`, and `@import` directives.

### With options (output style, load paths)

```java
import com.sass.CompileOptions;
import com.sass.OutputStyle;
import java.nio.file.Path;
import java.util.List;

var options = new CompileOptions(
    List.of(Path.of("vendor"), Path.of("lib")),  // additional load paths
    OutputStyle.COMPRESSED                         // output style
);

// File compilation with options
CompileResult result = SassCompiler.compileFile(
    Path.of("src/styles/main.scss"), options);

// String compilation with options
CompileResult result2 = SassCompiler.compile(scssString, options);
```

### Compressed output (shorthand)

```java
CompileResult result = SassCompiler.compileCompressed(".foo { color: red; }");
// .foo{color:red}
```

### Error handling

```java
import com.sass.exception.SassException;

try {
    SassCompiler.compile("invalid { {{ }");
} catch (SassException e) {
    System.err.println(e.getMessage());
    // SassException includes source span with file, line, column
    if (e.getSpan() != null) {
        System.err.println(e.getSpan().highlight());
    }
}
```

### CompileResult

`CompileResult` is a record with two fields:

| Method        | Type     | Description                                      |
|---------------|----------|--------------------------------------------------|
| `css()`       | `String` | The compiled CSS text                            |
| `sourceMap()` | `String` | Source map JSON (currently always `null`)         |

## Supported Features

- Variables (`$name: value`)
- Nesting (selectors and properties)
- Parent selector (`&`)
- Mixins (`@mixin` / `@include` / `@content`)
- Functions (`@function` / `@return`)
- Control flow (`@if` / `@else`, `@for`, `@each`, `@while`)
- String interpolation (`#{...}`)
- Arithmetic (`+`, `-`, `*`, `/`, `%`)
- Comments (loud `/* */` and silent `//`)
- Module system (`@use`, `@forward`, `@import`) with deduplication
- `@media`, `@supports`, `@at-root` with proper bubbling
- `@debug`, `@warn`, `@error`
- Built-in modules: `sass:math`, `sass:color`, `sass:string`, `sass:list`, `sass:map`, `sass:meta`
- Built-in functions: `if()`, `rgb()`, `rgba()`, `hsl()`, `hsla()`, `darken()`, `lighten()`, `mix()`, `type-of()`, `inspect()`, `unit()`, `unitless()`, `variable-exists()`, `global-variable-exists()`, `function-exists()`, `mixin-exists()`, and more
- Expanded and compressed output styles

## Not Yet Implemented

- `@extend` / placeholder selectors
- Source maps
- Nested `@media` query merging (functionally correct, just not flattened)
- Indented (`.sass`) syntax
- Full dart-sass color serialization (`transparent`, short hex)

## License

Same license as [dart-sass](https://github.com/sass/dart-sass/blob/main/LICENSE).
