package com.sass.parse;

/**
 * Character code constants used throughout the parser.
 * Named to match the dart charcode package conventions.
 */
public final class CharCodes {
    private CharCodes() {}

    // Control characters
    public static final int $nul = 0x00;
    public static final int $tab = 0x09;
    public static final int $lf = 0x0A;
    public static final int $vt = 0x0B;
    public static final int $ff = 0x0C;
    public static final int $cr = 0x0D;

    // Punctuation and symbols
    public static final int $space = 0x20;
    public static final int $exclamation = 0x21;
    public static final int $doubleQuote = 0x22;
    public static final int $hash = 0x23;
    public static final int $dollar = 0x24;
    public static final int $percent = 0x25;
    public static final int $ampersand = 0x26;
    public static final int $singleQuote = 0x27;
    public static final int $lparen = 0x28;
    public static final int $rparen = 0x29;
    public static final int $asterisk = 0x2A;
    public static final int $plus = 0x2B;
    public static final int $comma = 0x2C;
    public static final int $minus = 0x2D;
    public static final int $dot = 0x2E;
    public static final int $slash = 0x2F;

    // Digits
    public static final int $0 = 0x30;
    public static final int $1 = 0x31;
    public static final int $2 = 0x32;
    public static final int $3 = 0x33;
    public static final int $4 = 0x34;
    public static final int $5 = 0x35;
    public static final int $6 = 0x36;
    public static final int $7 = 0x37;
    public static final int $8 = 0x38;
    public static final int $9 = 0x39;

    // More punctuation
    public static final int $colon = 0x3A;
    public static final int $semicolon = 0x3B;
    public static final int $lt = 0x3C;
    public static final int $equal = 0x3D;
    public static final int $gt = 0x3E;
    public static final int $question = 0x3F;
    public static final int $at = 0x40;

    // Uppercase letters
    public static final int $A = 0x41;
    public static final int $B = 0x42;
    public static final int $C = 0x43;
    public static final int $D = 0x44;
    public static final int $E = 0x45;
    public static final int $F = 0x46;
    public static final int $G = 0x47;
    public static final int $H = 0x48;
    public static final int $I = 0x49;
    public static final int $J = 0x4A;
    public static final int $K = 0x4B;
    public static final int $L = 0x4C;
    public static final int $M = 0x4D;
    public static final int $N = 0x4E;
    public static final int $O = 0x4F;
    public static final int $P = 0x50;
    public static final int $Q = 0x51;
    public static final int $R = 0x52;
    public static final int $S = 0x53;
    public static final int $T = 0x54;
    public static final int $U = 0x55;
    public static final int $V = 0x56;
    public static final int $W = 0x57;
    public static final int $X = 0x58;
    public static final int $Y = 0x59;
    public static final int $Z = 0x5A;

    // Brackets
    public static final int $lbracket = 0x5B;
    public static final int $backslash = 0x5C;
    public static final int $rbracket = 0x5D;
    public static final int $caret = 0x5E;
    public static final int $underscore = 0x5F;
    public static final int $backtick = 0x60;

    // Lowercase letters
    public static final int $a = 0x61;
    public static final int $b = 0x62;
    public static final int $c = 0x63;
    public static final int $d = 0x64;
    public static final int $e = 0x65;
    public static final int $f = 0x66;
    public static final int $g = 0x67;
    public static final int $h = 0x68;
    public static final int $i = 0x69;
    public static final int $j = 0x6A;
    public static final int $k = 0x6B;
    public static final int $l = 0x6C;
    public static final int $m = 0x6D;
    public static final int $n = 0x6E;
    public static final int $o = 0x6F;
    public static final int $p = 0x70;
    public static final int $q = 0x71;
    public static final int $r = 0x72;
    public static final int $s = 0x73;
    public static final int $t = 0x74;
    public static final int $u = 0x75;
    public static final int $v = 0x76;
    public static final int $w = 0x77;
    public static final int $x = 0x78;
    public static final int $y = 0x79;
    public static final int $z = 0x7A;

    // Braces
    public static final int $lbrace = 0x7B;
    public static final int $pipe = 0x7C;
    public static final int $rbrace = 0x7D;
    public static final int $tilde = 0x7E;
    public static final int $del = 0x7F;
}
