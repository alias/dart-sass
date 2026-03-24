package com.sass.importer;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves @import/@use URLs to files on the filesystem.
 *
 * <p>Implements the dart-sass resolution algorithm: partial files ({@code _name.scss}),
 * extension probing ({@code .scss}, {@code .sass}, {@code .css}), index files,
 * and load paths.</p>
 *
 * <p>Ported from {@code lib/src/importer/filesystem.dart} and
 * {@code lib/src/importer/utils.dart}.</p>
 */
public final class FilesystemImporter {

    private final List<Path> loadPaths;

    public FilesystemImporter(List<Path> loadPaths) {
        this.loadPaths = List.copyOf(loadPaths);
    }

    /**
     * The result of a successful import resolution.
     *
     * @param path the resolved canonical file path
     * @param contents the file contents
     * @param sourceUri the canonical URI for source map references
     */
    public record ImportResult(Path path, String contents, URI sourceUri) {}

    /**
     * Resolves an import URL relative to the given base URI.
     *
     * <p>Algorithm (matches dart-sass {@code resolveImportPath}):</p>
     * <ol>
     *   <li>Resolve url relative to baseUri's directory</li>
     *   <li>If url has extension: try partial then regular</li>
     *   <li>If no extension: try .scss, .sass, .css — for each, partial then regular</li>
     *   <li>If nothing found: try as directory index (_index.scss, index.scss, etc.)</li>
     *   <li>If all relative attempts fail: repeat for each load path</li>
     *   <li>Throw if ambiguous (multiple matches across extensions)</li>
     * </ol>
     *
     * @param url the import URL string from the @import/@use rule
     * @param baseUri the URI of the file containing the import, or null for string input
     * @return the resolved import result, or null if not found
     * @throws IOException if file reading fails
     * @throws SassImportException if the import is ambiguous
     */
    public @Nullable ImportResult resolve(String url, @Nullable URI baseUri) throws IOException {
        // 1. Try relative to the importing file's directory
        if (baseUri != null) {
            Path basePath;
            try {
                basePath = Path.of(baseUri).getParent();
            } catch (Exception e) {
                basePath = null;
            }
            if (basePath != null) {
                var result = resolveImportPath(basePath.resolve(url).normalize().toString());
                if (result != null) return readResult(Path.of(result));
            }
        }

        // 2. Try each load path
        for (Path loadPath : loadPaths) {
            var result = resolveImportPath(loadPath.resolve(url).normalize().toString());
            if (result != null) return readResult(Path.of(result));
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Resolution algorithm (ported from lib/src/importer/utils.dart)
    // -----------------------------------------------------------------------

    /**
     * Resolves an import path to a concrete file path.
     *
     * <p>Ported from {@code resolveImportPath} in {@code lib/src/importer/utils.dart}.</p>
     */
    @Nullable String resolveImportPath(String path) {
        var extension = getExtension(path);
        if (".sass".equals(extension) || ".scss".equals(extension) || ".css".equals(extension)) {
            // Has explicit extension: try partial then regular
            return exactlyOne(tryPath(path));
        }

        // No extension: try with extensions, then as directory
        var result = exactlyOne(tryPathWithExtensions(path));
        if (result != null) return result;
        return tryPathAsDirectory(path);
    }

    /**
     * Tries path with .scss, .sass, and .css extensions in order.
     * Only tries .css if .scss and .sass didn't match.
     *
     * <p>Ported from {@code _tryPathWithExtensions}.</p>
     */
    private List<String> tryPathWithExtensions(String path) {
        var result = new ArrayList<>(tryPath(path + ".scss"));
        result.addAll(tryPath(path + ".sass"));
        if (!result.isEmpty()) return result;
        return tryPath(path + ".css");
    }

    /**
     * Returns existing paths for the given path and its partial variant.
     * Partial ({@code _name}) is checked first.
     *
     * <p>Ported from {@code _tryPath}.</p>
     */
    private List<String> tryPath(String path) {
        var p = Path.of(path);
        var result = new ArrayList<String>(2);

        // Try partial: _name.ext
        var fileName = p.getFileName().toString();
        if (!fileName.startsWith("_")) {
            var partial = p.resolveSibling("_" + fileName);
            if (Files.isRegularFile(partial)) {
                result.add(partial.toString());
            }
        }

        // Try regular: name.ext
        if (Files.isRegularFile(p)) {
            result.add(p.toString());
        }

        return result;
    }

    /**
     * Checks if path is a directory and tries to resolve index files.
     *
     * <p>Ported from {@code _tryPathAsDirectory}.</p>
     */
    private @Nullable String tryPathAsDirectory(String path) {
        var dir = Path.of(path);
        if (!Files.isDirectory(dir)) return null;

        return exactlyOne(tryPathWithExtensions(
                dir.resolve("index").toString()));
    }

    /**
     * Returns the single path from the list, throws if ambiguous, returns null if empty.
     *
     * <p>Ported from {@code _exactlyOne}.</p>
     */
    private @Nullable String exactlyOne(List<String> paths) {
        if (paths.isEmpty()) return null;
        if (paths.size() == 1) return paths.get(0);
        var sb = new StringBuilder("It's not clear which file to import. Found:\n");
        for (var p : paths) {
            sb.append("  ").append(p).append('\n');
        }
        throw new SassImportException(sb.toString().trim());
    }

    /**
     * Returns the extension of a path (e.g. ".scss"), or empty string if none.
     */
    private String getExtension(String path) {
        var lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        var lastDot = path.lastIndexOf('.');
        if (lastDot <= lastSlash) return "";
        return path.substring(lastDot);
    }

    /**
     * Reads a file and produces an ImportResult.
     */
    private ImportResult readResult(Path path) throws IOException {
        Path canonical = path.toAbsolutePath().normalize();
        // Use toRealPath to resolve symlinks if possible, fall back to normalized absolute
        try {
            canonical = path.toRealPath();
        } catch (IOException ignored) {
            // Fall back to absolute normalized path
        }
        String contents = Files.readString(canonical);
        URI uri = canonical.toUri();
        return new ImportResult(canonical, contents, uri);
    }
}
