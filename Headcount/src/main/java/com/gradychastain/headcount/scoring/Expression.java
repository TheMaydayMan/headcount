package com.gradychastain.headcount.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * A small math expression language for score formulas and conditions.
 * <ul>
 *   <li>numbers, variables ({@code unique}, {@code active}, ...), and {@code %placeholders%}</li>
 *   <li>{@code + - * / %} and {@code ^} (power, right-associative), parentheses, unary minus</li>
 *   <li>comparisons {@code < <= > >= == !=}, logic {@code && || !}, and {@code cond ? a : b} (true is 1, false is 0)</li>
 *   <li>functions: min, max, avg, floor, ceil, round(x[, digits]), trunc, abs, sign, pow, sqrt, cbrt, exp,
 *       log(x[, base]), ln, log10, log2, clamp(x, lo, hi), if(cond, a, b), sin, cos, tan</li>
 *   <li>constants: pi, e, true, false</li>
 * </ul>
 * Unknown variables evaluate to 0 and are reported through {@link #variables()} at load time.
 */
public final class Expression {
    @FunctionalInterface
    private interface Node {
        double eval(Function<String, Double> vars);
    }

    private static final Set<String> FUNCTIONS = Set.of("min", "max", "avg", "floor", "ceil", "round", "trunc", "abs", "sign", "pow",
            "sqrt", "cbrt", "exp", "log", "ln", "log10", "log2", "clamp", "if", "sin", "cos", "tan");

    private final String source;
    private final Node root;
    private final Set<String> variables;
    private final Set<String> placeholders;

    private Expression(String source, Node root, Set<String> variables, Set<String> placeholders) {
        this.source = source;
        this.root = root;
        this.variables = Collections.unmodifiableSet(variables);
        this.placeholders = Collections.unmodifiableSet(placeholders);
    }

    public static Expression parse(String source) {
        Parser p = new Parser(source);
        Node n = p.expression();
        p.skip();
        if (p.pos < source.length()) throw p.error("unexpected '" + source.charAt(p.pos) + "'");
        return new Expression(source, n, p.vars, p.placeholders);
    }

    public String source() {
        return source;
    }

    /** Variable names used (lowercase). */
    public Set<String> variables() {
        return variables;
    }

    /** Placeholders used, with their percent signs. */
    public Set<String> placeholders() {
        return placeholders;
    }

    /** Evaluates; {@code vars} returns null for unknown names, which count as 0. */
    public double evaluate(Function<String, Double> vars) {
        return root.eval(name -> {
            Double v = vars.apply(name);
            return v == null || v.isNaN() ? 0.0 : v;
        });
    }

    private static boolean truthy(double v) {
        return v != 0 && !Double.isNaN(v);
    }

    private static final class Parser {
        final String s;
        int pos;
        final Set<String> vars = new LinkedHashSet<>();
        final Set<String> placeholders = new LinkedHashSet<>();

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + (pos + 1) + " in '" + s + "'");
        }

        void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        boolean eat(String token) {
            skip();
            if (s.startsWith(token, pos)) {
                // do not let "<" eat the first half of "<=" etc.
                if ((token.equals("<") || token.equals(">") || token.equals("!") || token.equals("=")) && pos + 1 < s.length() && s.charAt(pos + 1) == '=') return false;
                if ((token.equals("&") || token.equals("|")) && pos + 1 < s.length() && s.charAt(pos + 1) == token.charAt(0)) return false;
                pos += token.length();
                return true;
            }
            return false;
        }

        Node expression() {
            Node cond = or();
            if (eat("?")) {
                Node a = expression();
                if (!eat(":")) throw error("expected ':'");
                Node b = expression();
                return v -> truthy(cond.eval(v)) ? a.eval(v) : b.eval(v);
            }
            return cond;
        }

        Node or() {
            Node left = and();
            while (eat("||")) {
                Node l = left, r = and();
                left = v -> truthy(l.eval(v)) || truthy(r.eval(v)) ? 1 : 0;
            }
            return left;
        }

        Node and() {
            Node left = equality();
            while (eat("&&")) {
                Node l = left, r = equality();
                left = v -> truthy(l.eval(v)) && truthy(r.eval(v)) ? 1 : 0;
            }
            return left;
        }

        Node equality() {
            Node left = comparison();
            while (true) {
                Node l = left;
                if (eat("==")) {
                    Node r = comparison();
                    left = v -> l.eval(v) == r.eval(v) ? 1 : 0;
                } else if (eat("!=")) {
                    Node r = comparison();
                    left = v -> l.eval(v) != r.eval(v) ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        Node comparison() {
            Node left = additive();
            while (true) {
                Node l = left;
                if (eat("<=")) {
                    Node r = additive();
                    left = v -> l.eval(v) <= r.eval(v) ? 1 : 0;
                } else if (eat(">=")) {
                    Node r = additive();
                    left = v -> l.eval(v) >= r.eval(v) ? 1 : 0;
                } else if (eat("<")) {
                    Node r = additive();
                    left = v -> l.eval(v) < r.eval(v) ? 1 : 0;
                } else if (eat(">")) {
                    Node r = additive();
                    left = v -> l.eval(v) > r.eval(v) ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        Node additive() {
            Node left = multiplicative();
            while (true) {
                Node l = left;
                if (eat("+")) {
                    Node r = multiplicative();
                    left = v -> l.eval(v) + r.eval(v);
                } else if (eat("-")) {
                    Node r = multiplicative();
                    left = v -> l.eval(v) - r.eval(v);
                } else {
                    return left;
                }
            }
        }

        Node multiplicative() {
            Node left = unary();
            while (true) {
                Node l = left;
                skip();
                if (pos < s.length() && s.charAt(pos) == '%' && isPlaceholderStart()) return left;
                if (eat("*")) {
                    Node r = unary();
                    left = v -> l.eval(v) * r.eval(v);
                } else if (eat("/")) {
                    Node r = unary();
                    left = v -> l.eval(v) / r.eval(v);
                } else if (eat("%")) {
                    Node r = unary();
                    left = v -> l.eval(v) % r.eval(v);
                } else {
                    return left;
                }
            }
        }

        /** A '%' starts a placeholder when it is followed by a name and a closing '%' with no spaces between. */
        boolean isPlaceholderStart() {
            int close = s.indexOf('%', pos + 1);
            if (close < 0) return false;
            String inner = s.substring(pos + 1, close);
            return !inner.isEmpty() && inner.chars().noneMatch(Character::isWhitespace) && Character.isLetter(inner.charAt(0));
        }

        Node unary() {
            if (eat("-")) {
                Node n = unary();
                return v -> -n.eval(v);
            }
            if (eat("+")) return unary();
            if (eat("!")) {
                Node n = unary();
                return v -> truthy(n.eval(v)) ? 0 : 1;
            }
            return power();
        }

        Node power() {
            Node base = primary();
            if (eat("^")) {
                Node exp = unary();
                return v -> Math.pow(base.eval(v), exp.eval(v));
            }
            return base;
        }

        Node primary() {
            skip();
            if (pos >= s.length()) throw error("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                Node n = expression();
                if (!eat(")")) throw error("expected ')'");
                return n;
            }
            if (c == '%') {
                int close = s.indexOf('%', pos + 1);
                if (close < 0) throw error("unclosed placeholder");
                String ph = s.substring(pos, close + 1);
                pos = close + 1;
                placeholders.add(ph);
                return v -> v.apply(ph);
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E') && pos + 1 < s.length()
                        && (Character.isDigit(s.charAt(pos + 1)) || s.charAt(pos + 1) == '-' || s.charAt(pos + 1) == '+')) {
                    pos += 2;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                }
                double value;
                try {
                    value = Double.parseDouble(s.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw error("bad number '" + s.substring(start, pos) + "'");
                }
                return v -> value;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) pos++;
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                skip();
                if (pos < s.length() && s.charAt(pos) == '(') {
                    pos++;
                    List<Node> args = new ArrayList<>();
                    skip();
                    if (!eat(")")) {
                        do {
                            args.add(expression());
                        } while (eat(","));
                        if (!eat(")")) throw error("expected ')' after arguments to " + name);
                    }
                    return function(name, args);
                }
                switch (name) {
                    case "pi": return v -> Math.PI;
                    case "e": return v -> Math.E;
                    case "true": return v -> 1;
                    case "false": return v -> 0;
                    default:
                        vars.add(name);
                        return v -> v.apply(name);
                }
            }
            throw error("unexpected '" + c + "'");
        }

        Node function(String name, List<Node> a) {
            if (!FUNCTIONS.contains(name)) throw error("unknown function '" + name + "'");
            int n = a.size();
            switch (name) {
                case "min", "max", "avg" -> {
                    if (n == 0) throw error(name + " needs at least one argument");
                }
                case "round", "log" -> {
                    if (n < 1 || n > 2) throw error(name + " takes 1 or 2 arguments");
                }
                case "pow" -> {
                    if (n != 2) throw error("pow takes 2 arguments");
                }
                case "clamp", "if" -> {
                    if (n != 3) throw error(name + " takes 3 arguments");
                }
                default -> {
                    if (n != 1) throw error(name + " takes 1 argument");
                }
            }
            return switch (name) {
                case "min" -> v -> {
                    double m = Double.POSITIVE_INFINITY;
                    for (Node x : a) m = Math.min(m, x.eval(v));
                    return m;
                };
                case "max" -> v -> {
                    double m = Double.NEGATIVE_INFINITY;
                    for (Node x : a) m = Math.max(m, x.eval(v));
                    return m;
                };
                case "avg" -> v -> {
                    double sum = 0;
                    for (Node x : a) sum += x.eval(v);
                    return sum / a.size();
                };
                case "floor" -> v -> Math.floor(a.get(0).eval(v));
                case "ceil" -> v -> Math.ceil(a.get(0).eval(v));
                case "round" -> n == 1 ? v -> (double) Math.round(a.get(0).eval(v)) : v -> {
                    double f = Math.pow(10, Math.round(a.get(1).eval(v)));
                    return Math.round(a.get(0).eval(v) * f) / f;
                };
                case "trunc" -> v -> (double) (long) a.get(0).eval(v);
                case "abs" -> v -> Math.abs(a.get(0).eval(v));
                case "sign" -> v -> Math.signum(a.get(0).eval(v));
                case "pow" -> v -> Math.pow(a.get(0).eval(v), a.get(1).eval(v));
                case "sqrt" -> v -> Math.sqrt(a.get(0).eval(v));
                case "cbrt" -> v -> Math.cbrt(a.get(0).eval(v));
                case "exp" -> v -> Math.exp(a.get(0).eval(v));
                case "log" -> n == 1 ? v -> Math.log(a.get(0).eval(v)) : v -> Math.log(a.get(0).eval(v)) / Math.log(a.get(1).eval(v));
                case "ln" -> v -> Math.log(a.get(0).eval(v));
                case "log10" -> v -> Math.log10(a.get(0).eval(v));
                case "log2" -> v -> Math.log(a.get(0).eval(v)) / Math.log(2);
                case "clamp" -> v -> Math.max(a.get(1).eval(v), Math.min(a.get(2).eval(v), a.get(0).eval(v)));
                case "if" -> v -> truthy(a.get(0).eval(v)) ? a.get(1).eval(v) : a.get(2).eval(v);
                case "sin" -> v -> Math.sin(a.get(0).eval(v));
                case "cos" -> v -> Math.cos(a.get(0).eval(v));
                case "tan" -> v -> Math.tan(a.get(0).eval(v));
                default -> throw error("unknown function '" + name + "'");
            };
        }
    }
}
