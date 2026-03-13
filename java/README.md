# Sass Compiler for Java

A pure-Java implementation of the [Sass](https://sass-lang.com/) CSS preprocessor, ported from [dart-sass](https://github.com/sass/dart-sass). Compiles SCSS to CSS with no native dependencies.

**Status:** Early development (0.1.0-SNAPSHOT). Core compilation works — variables, nesting, mixins, functions, control flow (`@if`, `@for`, `@each`, `@while`), interpolation, and arithmetic are supported. Module system (`@use`/`@forward`), `@extend`, and the full built-in function library are not yet implemented.

## Requirements

- Java 17+
- Maven 3.8+

## Building

```bash
cd java
mvn clean install
```

Run tests only:

```bash
mvn test
```

## Maven Dependency

Once installed to your local repository (`mvn install`), add this to your `pom.xml`:

```xml
<dependency>
    <groupId>com.sass</groupId>
    <artifactId>sass-compiler</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic compilation

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

### Compressed output

```java
CompileResult result = SassCompiler.compileCompressed(".foo { color: red; }");
// .foo{color:red}
```

### Output style option

```java
import com.sass.OutputStyle;

CompileResult result = SassCompiler.compile(scss, OutputStyle.COMPRESSED);
```

### Error handling

```java
import com.sass.exception.SassException;

try {
    SassCompiler.compile("invalid { {{ }");
} catch (SassException e) {
    System.err.println(e.getMessage());
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
- `@media`, `@supports`, `@at-root`
- `@debug`, `@warn`, `@error`
- Built-in functions: `if()`, `type-of()`, `inspect()`, `unit()`, `unitless()`, `variable-exists()`, `global-variable-exists()`, `function-exists()`, `mixin-exists()`
- Expanded and compressed output styles

## Not Yet Implemented

- Module system (`@use`, `@forward`)
- `@extend` / placeholder selectors
- Source maps
- File-based compilation / `@import`
- Full built-in function library (color, math, string, list, map, selector, meta modules)
- Indented (`.sass`) syntax

## License

Same license as [dart-sass](https://github.com/sass/dart-sass/blob/main/LICENSE).
