package com.sass.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FilesystemImporter} — the Sass file resolver.
 *
 * <p>Tests the resolution algorithm ported from dart-sass:
 * partial files, extension probing, index files, load paths, and ambiguity detection.</p>
 */
class FilesystemImporterTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // Basic resolution
    // -----------------------------------------------------------------------

    @Test
    void resolves_scss_file_by_exact_path() throws IOException {
        Files.writeString(tempDir.resolve("style.scss"), ".a { color: red; }");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("style.scss", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo(".a { color: red; }");
    }

    @Test
    void returns_null_for_nonexistent_file() throws IOException {
        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("nonexistent", baseUri);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Partial resolution (_name.scss)
    // -----------------------------------------------------------------------

    @Test
    void resolves_partial_file() throws IOException {
        Files.writeString(tempDir.resolve("_variables.scss"), "$color: blue;");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("variables", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo("$color: blue;");
        assertThat(result.path().getFileName().toString()).isEqualTo("_variables.scss");
    }

    @Test
    void partial_preferred_over_regular_when_both_exist() throws IOException {
        Files.writeString(tempDir.resolve("_vars.scss"), "// partial");
        Files.writeString(tempDir.resolve("vars.scss"), "// regular");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();

        // When both exist, it should be ambiguous (dart-sass throws)
        assertThatThrownBy(() -> importer.resolve("vars", baseUri))
                .isInstanceOf(SassImportException.class)
                .hasMessageContaining("not clear");
    }

    // -----------------------------------------------------------------------
    // Extension probing
    // -----------------------------------------------------------------------

    @Test
    void probes_scss_extension() throws IOException {
        Files.writeString(tempDir.resolve("style.scss"), ".a {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("style", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.path().getFileName().toString()).isEqualTo("style.scss");
    }

    @Test
    void probes_css_extension_when_no_scss_or_sass() throws IOException {
        Files.writeString(tempDir.resolve("lib.css"), ".lib {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("lib", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.path().getFileName().toString()).isEqualTo("lib.css");
    }

    @Test
    void scss_preferred_over_css() throws IOException {
        Files.writeString(tempDir.resolve("theme.scss"), "// scss");
        Files.writeString(tempDir.resolve("theme.css"), "/* css */");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("theme", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.path().getFileName().toString()).isEqualTo("theme.scss");
    }

    // -----------------------------------------------------------------------
    // Index file resolution (dir/index.scss)
    // -----------------------------------------------------------------------

    @Test
    void resolves_directory_index_file() throws IOException {
        var dir = tempDir.resolve("components");
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("_index.scss"), ".component {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("components", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo(".component {}");
        assertThat(result.path().getFileName().toString()).isEqualTo("_index.scss");
    }

    @Test
    void resolves_non_partial_index_file() throws IOException {
        var dir = tempDir.resolve("utils");
        Files.createDirectory(dir);
        Files.writeString(dir.resolve("index.scss"), ".util {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("utils", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.path().getFileName().toString()).isEqualTo("index.scss");
    }

    // -----------------------------------------------------------------------
    // Load paths
    // -----------------------------------------------------------------------

    @Test
    void resolves_from_load_path() throws IOException {
        var libDir = tempDir.resolve("lib");
        Files.createDirectory(libDir);
        Files.writeString(libDir.resolve("reset.scss"), "* { margin: 0; }");

        // base is in a different directory that doesn't contain reset.scss
        var srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);
        var baseUri = srcDir.resolve("main.scss").toUri();

        var importer = new FilesystemImporter(List.of(libDir));
        var result = importer.resolve("reset", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo("* { margin: 0; }");
    }

    @Test
    void relative_path_preferred_over_load_path() throws IOException {
        // Same file in both relative dir and load path
        var srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);
        Files.writeString(srcDir.resolve("common.scss"), "// relative");

        var libDir = tempDir.resolve("lib");
        Files.createDirectory(libDir);
        Files.writeString(libDir.resolve("common.scss"), "// load-path");

        var baseUri = srcDir.resolve("main.scss").toUri();
        var importer = new FilesystemImporter(List.of(libDir));
        var result = importer.resolve("common", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo("// relative");
    }

    @Test
    void load_paths_searched_in_order() throws IOException {
        var dir1 = tempDir.resolve("first");
        Files.createDirectory(dir1);
        Files.writeString(dir1.resolve("theme.scss"), "// first");

        var dir2 = tempDir.resolve("second");
        Files.createDirectory(dir2);
        Files.writeString(dir2.resolve("theme.scss"), "// second");

        var baseUri = tempDir.resolve("main.scss").toUri();
        var importer = new FilesystemImporter(List.of(dir1, dir2));
        var result = importer.resolve("theme", baseUri);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo("// first");
    }

    // -----------------------------------------------------------------------
    // Explicit extension
    // -----------------------------------------------------------------------

    @Test
    void resolves_explicit_scss_extension() throws IOException {
        Files.writeString(tempDir.resolve("style.scss"), ".ok {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("style.scss", baseUri);

        assertThat(result).isNotNull();
    }

    @Test
    void resolves_explicit_css_extension() throws IOException {
        Files.writeString(tempDir.resolve("normalize.css"), "html {}");

        var importer = new FilesystemImporter(List.of());
        var baseUri = tempDir.resolve("main.scss").toUri();
        var result = importer.resolve("normalize.css", baseUri);

        assertThat(result).isNotNull();
    }

    // -----------------------------------------------------------------------
    // No base URI (string input)
    // -----------------------------------------------------------------------

    @Test
    void resolves_from_load_path_when_no_base_uri() throws IOException {
        Files.writeString(tempDir.resolve("global.scss"), ".global {}");

        var importer = new FilesystemImporter(List.of(tempDir));
        var result = importer.resolve("global", null);

        assertThat(result).isNotNull();
        assertThat(result.contents()).isEqualTo(".global {}");
    }
}
