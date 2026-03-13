package com.sass.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps between CSS color names and their color values.
 *
 * <p>Contains all 148 standard CSS named colors (plus "transparent"),
 * used by the serializer to emit colors using named representations
 * when shorter than hex or functional notation.</p>
 */
public final class ColorNames {

    /**
     * A map from lowercase color names to their SassColor values.
     *
     * <p>The map is in reverse alphabetical order so that colors with
     * multiple names (e.g., "aqua"/"cyan", "fuchsia"/"magenta") will
     * use the alphabetically first option in {@link #COLORS_TO_NAMES}.</p>
     */
    public static final Map<String, SassColor> NAMES_TO_COLORS;

    /**
     * A map from SassColor values to their lowercase color names.
     * Used for reverse lookup during serialization.
     */
    public static final Map<SassColor, String> COLORS_TO_NAMES;

    static {
        var namesToColors = new LinkedHashMap<String, SassColor>();

        // Note: these are in reverse alphabetical order so that colors with
        // multiple names will use the alphabetically first option in
        // COLORS_TO_NAMES.
        namesToColors.put("yellowgreen", SassColor.rgb(0x9A, 0xCD, 0x32));
        namesToColors.put("yellow", SassColor.rgb(0xFF, 0xFF, 0x00));
        namesToColors.put("whitesmoke", SassColor.rgb(0xF5, 0xF5, 0xF5));
        namesToColors.put("white", SassColor.rgb(0xFF, 0xFF, 0xFF));
        namesToColors.put("wheat", SassColor.rgb(0xF5, 0xDE, 0xB3));
        namesToColors.put("violet", SassColor.rgb(0xEE, 0x82, 0xEE));
        namesToColors.put("turquoise", SassColor.rgb(0x40, 0xE0, 0xD0));
        namesToColors.put("transparent", SassColor.rgb(0, 0, 0, 0));
        namesToColors.put("tomato", SassColor.rgb(0xFF, 0x63, 0x47));
        namesToColors.put("thistle", SassColor.rgb(0xD8, 0xBF, 0xD8));
        namesToColors.put("teal", SassColor.rgb(0x00, 0x80, 0x80));
        namesToColors.put("tan", SassColor.rgb(0xD2, 0xB4, 0x8C));
        namesToColors.put("steelblue", SassColor.rgb(0x46, 0x82, 0xB4));
        namesToColors.put("springgreen", SassColor.rgb(0x00, 0xFF, 0x7F));
        namesToColors.put("snow", SassColor.rgb(0xFF, 0xFA, 0xFA));
        namesToColors.put("slategrey", SassColor.rgb(0x70, 0x80, 0x90));
        namesToColors.put("slategray", SassColor.rgb(0x70, 0x80, 0x90));
        namesToColors.put("slateblue", SassColor.rgb(0x6A, 0x5A, 0xCD));
        namesToColors.put("skyblue", SassColor.rgb(0x87, 0xCE, 0xEB));
        namesToColors.put("silver", SassColor.rgb(0xC0, 0xC0, 0xC0));
        namesToColors.put("sienna", SassColor.rgb(0xA0, 0x52, 0x2D));
        namesToColors.put("seashell", SassColor.rgb(0xFF, 0xF5, 0xEE));
        namesToColors.put("seagreen", SassColor.rgb(0x2E, 0x8B, 0x57));
        namesToColors.put("sandybrown", SassColor.rgb(0xF4, 0xA4, 0x60));
        namesToColors.put("salmon", SassColor.rgb(0xFA, 0x80, 0x72));
        namesToColors.put("saddlebrown", SassColor.rgb(0x8B, 0x45, 0x13));
        namesToColors.put("royalblue", SassColor.rgb(0x41, 0x69, 0xE1));
        namesToColors.put("rosybrown", SassColor.rgb(0xBC, 0x8F, 0x8F));
        namesToColors.put("red", SassColor.rgb(0xFF, 0x00, 0x00));
        namesToColors.put("rebeccapurple", SassColor.rgb(0x66, 0x33, 0x99));
        namesToColors.put("purple", SassColor.rgb(0x80, 0x00, 0x80));
        namesToColors.put("powderblue", SassColor.rgb(0xB0, 0xE0, 0xE6));
        namesToColors.put("plum", SassColor.rgb(0xDD, 0xA0, 0xDD));
        namesToColors.put("pink", SassColor.rgb(0xFF, 0xC0, 0xCB));
        namesToColors.put("peru", SassColor.rgb(0xCD, 0x85, 0x3F));
        namesToColors.put("peachpuff", SassColor.rgb(0xFF, 0xDA, 0xB9));
        namesToColors.put("papayawhip", SassColor.rgb(0xFF, 0xEF, 0xD5));
        namesToColors.put("palevioletred", SassColor.rgb(0xDB, 0x70, 0x93));
        namesToColors.put("paleturquoise", SassColor.rgb(0xAF, 0xEE, 0xEE));
        namesToColors.put("palegreen", SassColor.rgb(0x98, 0xFB, 0x98));
        namesToColors.put("palegoldenrod", SassColor.rgb(0xEE, 0xE8, 0xAA));
        namesToColors.put("orchid", SassColor.rgb(0xDA, 0x70, 0xD6));
        namesToColors.put("orangered", SassColor.rgb(0xFF, 0x45, 0x00));
        namesToColors.put("orange", SassColor.rgb(0xFF, 0xA5, 0x00));
        namesToColors.put("olivedrab", SassColor.rgb(0x6B, 0x8E, 0x23));
        namesToColors.put("olive", SassColor.rgb(0x80, 0x80, 0x00));
        namesToColors.put("oldlace", SassColor.rgb(0xFD, 0xF5, 0xE6));
        namesToColors.put("navy", SassColor.rgb(0x00, 0x00, 0x80));
        namesToColors.put("navajowhite", SassColor.rgb(0xFF, 0xDE, 0xAD));
        namesToColors.put("moccasin", SassColor.rgb(0xFF, 0xE4, 0xB5));
        namesToColors.put("mistyrose", SassColor.rgb(0xFF, 0xE4, 0xE1));
        namesToColors.put("mintcream", SassColor.rgb(0xF5, 0xFF, 0xFA));
        namesToColors.put("midnightblue", SassColor.rgb(0x19, 0x19, 0x70));
        namesToColors.put("mediumvioletred", SassColor.rgb(0xC7, 0x15, 0x85));
        namesToColors.put("mediumturquoise", SassColor.rgb(0x48, 0xD1, 0xCC));
        namesToColors.put("mediumspringgreen", SassColor.rgb(0x00, 0xFA, 0x9A));
        namesToColors.put("mediumslateblue", SassColor.rgb(0x7B, 0x68, 0xEE));
        namesToColors.put("mediumseagreen", SassColor.rgb(0x3C, 0xB3, 0x71));
        namesToColors.put("mediumpurple", SassColor.rgb(0x93, 0x70, 0xDB));
        namesToColors.put("mediumorchid", SassColor.rgb(0xBA, 0x55, 0xD3));
        namesToColors.put("mediumblue", SassColor.rgb(0x00, 0x00, 0xCD));
        namesToColors.put("mediumaquamarine", SassColor.rgb(0x66, 0xCD, 0xAA));
        namesToColors.put("maroon", SassColor.rgb(0x80, 0x00, 0x00));
        namesToColors.put("magenta", SassColor.rgb(0xFF, 0x00, 0xFF));
        namesToColors.put("linen", SassColor.rgb(0xFA, 0xF0, 0xE6));
        namesToColors.put("limegreen", SassColor.rgb(0x32, 0xCD, 0x32));
        namesToColors.put("lime", SassColor.rgb(0x00, 0xFF, 0x00));
        namesToColors.put("lightyellow", SassColor.rgb(0xFF, 0xFF, 0xE0));
        namesToColors.put("lightsteelblue", SassColor.rgb(0xB0, 0xC4, 0xDE));
        namesToColors.put("lightslategrey", SassColor.rgb(0x77, 0x88, 0x99));
        namesToColors.put("lightslategray", SassColor.rgb(0x77, 0x88, 0x99));
        namesToColors.put("lightskyblue", SassColor.rgb(0x87, 0xCE, 0xFA));
        namesToColors.put("lightseagreen", SassColor.rgb(0x20, 0xB2, 0xAA));
        namesToColors.put("lightsalmon", SassColor.rgb(0xFF, 0xA0, 0x7A));
        namesToColors.put("lightpink", SassColor.rgb(0xFF, 0xB6, 0xC1));
        namesToColors.put("lightgrey", SassColor.rgb(0xD3, 0xD3, 0xD3));
        namesToColors.put("lightgreen", SassColor.rgb(0x90, 0xEE, 0x90));
        namesToColors.put("lightgray", SassColor.rgb(0xD3, 0xD3, 0xD3));
        namesToColors.put("lightgoldenrodyellow", SassColor.rgb(0xFA, 0xFA, 0xD2));
        namesToColors.put("lightcyan", SassColor.rgb(0xE0, 0xFF, 0xFF));
        namesToColors.put("lightcoral", SassColor.rgb(0xF0, 0x80, 0x80));
        namesToColors.put("lightblue", SassColor.rgb(0xAD, 0xD8, 0xE6));
        namesToColors.put("lemonchiffon", SassColor.rgb(0xFF, 0xFA, 0xCD));
        namesToColors.put("lawngreen", SassColor.rgb(0x7C, 0xFC, 0x00));
        namesToColors.put("lavenderblush", SassColor.rgb(0xFF, 0xF0, 0xF5));
        namesToColors.put("lavender", SassColor.rgb(0xE6, 0xE6, 0xFA));
        namesToColors.put("khaki", SassColor.rgb(0xF0, 0xE6, 0x8C));
        namesToColors.put("ivory", SassColor.rgb(0xFF, 0xFF, 0xF0));
        namesToColors.put("indigo", SassColor.rgb(0x4B, 0x00, 0x82));
        namesToColors.put("indianred", SassColor.rgb(0xCD, 0x5C, 0x5C));
        namesToColors.put("hotpink", SassColor.rgb(0xFF, 0x69, 0xB4));
        namesToColors.put("honeydew", SassColor.rgb(0xF0, 0xFF, 0xF0));
        namesToColors.put("grey", SassColor.rgb(0x80, 0x80, 0x80));
        namesToColors.put("greenyellow", SassColor.rgb(0xAD, 0xFF, 0x2F));
        namesToColors.put("green", SassColor.rgb(0x00, 0x80, 0x00));
        namesToColors.put("gray", SassColor.rgb(0x80, 0x80, 0x80));
        namesToColors.put("goldenrod", SassColor.rgb(0xDA, 0xA5, 0x20));
        namesToColors.put("gold", SassColor.rgb(0xFF, 0xD7, 0x00));
        namesToColors.put("ghostwhite", SassColor.rgb(0xF8, 0xF8, 0xFF));
        namesToColors.put("gainsboro", SassColor.rgb(0xDC, 0xDC, 0xDC));
        namesToColors.put("fuchsia", SassColor.rgb(0xFF, 0x00, 0xFF));
        namesToColors.put("forestgreen", SassColor.rgb(0x22, 0x8B, 0x22));
        namesToColors.put("floralwhite", SassColor.rgb(0xFF, 0xFA, 0xF0));
        namesToColors.put("firebrick", SassColor.rgb(0xB2, 0x22, 0x22));
        namesToColors.put("dodgerblue", SassColor.rgb(0x1E, 0x90, 0xFF));
        namesToColors.put("dimgrey", SassColor.rgb(0x69, 0x69, 0x69));
        namesToColors.put("dimgray", SassColor.rgb(0x69, 0x69, 0x69));
        namesToColors.put("deepskyblue", SassColor.rgb(0x00, 0xBF, 0xFF));
        namesToColors.put("deeppink", SassColor.rgb(0xFF, 0x14, 0x93));
        namesToColors.put("darkviolet", SassColor.rgb(0x94, 0x00, 0xD3));
        namesToColors.put("darkturquoise", SassColor.rgb(0x00, 0xCE, 0xD1));
        namesToColors.put("darkslategrey", SassColor.rgb(0x2F, 0x4F, 0x4F));
        namesToColors.put("darkslategray", SassColor.rgb(0x2F, 0x4F, 0x4F));
        namesToColors.put("darkslateblue", SassColor.rgb(0x48, 0x3D, 0x8B));
        namesToColors.put("darkseagreen", SassColor.rgb(0x8F, 0xBC, 0x8F));
        namesToColors.put("darksalmon", SassColor.rgb(0xE9, 0x96, 0x7A));
        namesToColors.put("darkred", SassColor.rgb(0x8B, 0x00, 0x00));
        namesToColors.put("darkorchid", SassColor.rgb(0x99, 0x32, 0xCC));
        namesToColors.put("darkorange", SassColor.rgb(0xFF, 0x8C, 0x00));
        namesToColors.put("darkolivegreen", SassColor.rgb(0x55, 0x6B, 0x2F));
        namesToColors.put("darkmagenta", SassColor.rgb(0x8B, 0x00, 0x8B));
        namesToColors.put("darkkhaki", SassColor.rgb(0xBD, 0xB7, 0x6B));
        namesToColors.put("darkgrey", SassColor.rgb(0xA9, 0xA9, 0xA9));
        namesToColors.put("darkgreen", SassColor.rgb(0x00, 0x64, 0x00));
        namesToColors.put("darkgray", SassColor.rgb(0xA9, 0xA9, 0xA9));
        namesToColors.put("darkgoldenrod", SassColor.rgb(0xB8, 0x86, 0x0B));
        namesToColors.put("darkcyan", SassColor.rgb(0x00, 0x8B, 0x8B));
        namesToColors.put("darkblue", SassColor.rgb(0x00, 0x00, 0x8B));
        namesToColors.put("cyan", SassColor.rgb(0x00, 0xFF, 0xFF));
        namesToColors.put("crimson", SassColor.rgb(0xDC, 0x14, 0x3C));
        namesToColors.put("cornsilk", SassColor.rgb(0xFF, 0xF8, 0xDC));
        namesToColors.put("cornflowerblue", SassColor.rgb(0x64, 0x95, 0xED));
        namesToColors.put("coral", SassColor.rgb(0xFF, 0x7F, 0x50));
        namesToColors.put("chocolate", SassColor.rgb(0xD2, 0x69, 0x1E));
        namesToColors.put("chartreuse", SassColor.rgb(0x7F, 0xFF, 0x00));
        namesToColors.put("cadetblue", SassColor.rgb(0x5F, 0x9E, 0xA0));
        namesToColors.put("burlywood", SassColor.rgb(0xDE, 0xB8, 0x87));
        namesToColors.put("brown", SassColor.rgb(0xA5, 0x2A, 0x2A));
        namesToColors.put("blueviolet", SassColor.rgb(0x8A, 0x2B, 0xE2));
        namesToColors.put("blue", SassColor.rgb(0x00, 0x00, 0xFF));
        namesToColors.put("blanchedalmond", SassColor.rgb(0xFF, 0xEB, 0xCD));
        namesToColors.put("black", SassColor.rgb(0x00, 0x00, 0x00));
        namesToColors.put("bisque", SassColor.rgb(0xFF, 0xE4, 0xC4));
        namesToColors.put("beige", SassColor.rgb(0xF5, 0xF5, 0xDC));
        namesToColors.put("azure", SassColor.rgb(0xF0, 0xFF, 0xFF));
        namesToColors.put("aquamarine", SassColor.rgb(0x7F, 0xFF, 0xD4));
        namesToColors.put("aqua", SassColor.rgb(0x00, 0xFF, 0xFF));
        namesToColors.put("antiquewhite", SassColor.rgb(0xFA, 0xEB, 0xD7));
        namesToColors.put("aliceblue", SassColor.rgb(0xF0, 0xF8, 0xFF));

        NAMES_TO_COLORS = Collections.unmodifiableMap(namesToColors);

        // Build the reverse map: color -> name.
        // Since the map above is in reverse alphabetical order, iterating
        // forward and overwriting means the alphabetically-first name wins.
        var colorsToNames = new LinkedHashMap<SassColor, String>();
        for (var entry : namesToColors.entrySet()) {
            colorsToNames.put(entry.getValue(), entry.getKey());
        }
        COLORS_TO_NAMES = Collections.unmodifiableMap(colorsToNames);
    }

    private ColorNames() {}
}
