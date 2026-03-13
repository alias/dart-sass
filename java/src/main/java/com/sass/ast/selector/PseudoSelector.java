package com.sass.ast.selector;

import com.sass.util.FileSpan;
import com.sass.visitor.SelectorVisitor;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * A pseudo-class or pseudo-element selector.
 *
 * <p>Pseudo-classes are written with a single colon (e.g. {@code :hover}),
 * while pseudo-elements are written with a double colon (e.g. {@code ::before}).
 * Some pseudo-elements may be written with a single colon for historical reasons;
 * these have {@code isSyntacticClass} set to {@code true} but {@code isClass}
 * set to {@code false}.</p>
 */
public final class PseudoSelector extends SimpleSelector {

    private final String name;
    private final String normalizedName;
    private final boolean isClass;
    private final boolean isSyntacticClass;
    private final @Nullable String argument;
    private final @Nullable SelectorList selector;
    private final FileSpan span;

    /**
     * Creates a pseudo selector.
     *
     * @param name the pseudo name (without colons)
     * @param isClass whether this is a pseudo-class (true) or pseudo-element (false)
     * @param isSyntacticClass whether this was written with a single colon
     * @param argument the non-selector argument, or null
     * @param selector the selector argument (for :is(), :not(), etc.), or null
     * @param span the source span
     */
    public PseudoSelector(String name, boolean isClass, boolean isSyntacticClass,
                          @Nullable String argument, @Nullable SelectorList selector,
                          FileSpan span) {
        this.name = Objects.requireNonNull(name);
        this.normalizedName = name.toLowerCase(Locale.ROOT);
        this.isClass = isClass;
        this.isSyntacticClass = isSyntacticClass;
        this.argument = argument;
        this.selector = selector;
        this.span = Objects.requireNonNull(span);
    }

    /** Returns the pseudo name (without colons). */
    public String getName() {
        return name;
    }

    /** Returns the lowercase version of the pseudo name. */
    public String getNormalizedName() {
        return normalizedName;
    }

    /** Returns {@code true} if this is a pseudo-class, {@code false} if pseudo-element. */
    public boolean isClass() {
        return isClass;
    }

    /** Returns {@code true} if this was written with a single colon in the source. */
    public boolean isSyntacticClass() {
        return isSyntacticClass;
    }

    /** Returns the non-selector argument (e.g. "2n+1" in {@code :nth-child(2n+1)}), or null. */
    public @Nullable String getArgument() {
        return argument;
    }

    /** Returns the selector argument (e.g. in {@code :is(.foo, .bar)}), or null. */
    public @Nullable SelectorList getSelector() {
        return selector;
    }

    @Override
    public FileSpan getSpan() {
        return span;
    }

    /** Returns {@code true} if this is a pseudo-element. */
    public boolean isElement() {
        return !isClass;
    }

    /** Returns {@code true} if this is the {@code :host} pseudo-class. */
    public boolean isHost() {
        return isClass && "host".equals(normalizedName);
    }

    /** Returns {@code true} if this is the {@code :host-context} pseudo-class. */
    public boolean isHostContext() {
        return isClass && "host-context".equals(normalizedName);
    }

    @Override
    public int getSpecificity() {
        if (isElement()) {
            return 1;
        }

        if (selector != null) {
            // For :is(), :not(), :has(), :matches(), the specificity is the
            // maximum specificity of the sub-selectors.
            if ("is".equals(normalizedName) || "not".equals(normalizedName)
                    || "has".equals(normalizedName) || "matches".equals(normalizedName)) {
                int max = 0;
                for (ComplexSelector complex : selector.getComponents()) {
                    max = Math.max(max, complex.getSpecificity());
                }
                return max;
            }
        }

        return 1000;
    }

    @Override
    public <T> T accept(SelectorVisitor<T> visitor) {
        return visitor.visitPseudoSelector(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PseudoSelector other)) return false;
        return isClass == other.isClass
                && normalizedName.equals(other.normalizedName)
                && Objects.equals(argument, other.argument)
                && Objects.equals(selector, other.selector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedName, isClass, argument, selector);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(isSyntacticClass ? ":" : "::");
        sb.append(name);
        if (argument != null || selector != null) {
            sb.append('(');
            if (argument != null) {
                sb.append(argument);
                if (selector != null) {
                    sb.append(' ');
                }
            }
            if (selector != null) {
                sb.append(selector);
            }
            sb.append(')');
        }
        return sb.toString();
    }
}
