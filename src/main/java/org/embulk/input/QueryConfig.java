package org.embulk.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class QueryConfig {

    private final String name;
    private final Optional<String> value;
    private final Optional<List<String>> values;
    private final boolean expand;

    @JsonCreator
    public QueryConfig(
            @JsonProperty("name") String name,
            @JsonProperty("value") Optional<String> value,
            @JsonProperty("values") Optional<List<String>> values,
            @JsonProperty("expand") boolean expand) {
        this.name = name;
        this.value = value;
        this.values = values;
        this.expand = expand;
    }

    public List<Query> expand() {
        List<Query> dest;
        if (!expand) {
            if (values.isPresent()) {
                dest = new ArrayList<>(values.get().size());
                for (String s : values.get()) {
                    dest.add(new Query(name, s));
                }
            } else if (value.isPresent()) {
                dest = Lists.newArrayList(new Query(name, value.get()));
            } else {
                throw new IllegalArgumentException("value or values must be specified to 'params'");
            }
        } else {
            List<String> expanded = BraceExpansion.expand(value.get());
            dest = new ArrayList<>(expanded.size());
            for (String s : expanded) {
                dest.add(new Query(name, s));
            }
        }
        return dest;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("value")
    public Optional<String> getValue() {
        return value;
    }

    @JsonProperty("expand")
    public boolean isExpand() {
        return expand;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof QueryConfig)) {
            return false;
        }
        QueryConfig other = (QueryConfig) obj;
        return Objects.equal(this.name, other.name) &&
                Objects.equal(value, other.value) &&
                Objects.equal(expand, other.expand);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, value, expand);
    }

    @Override
    public String toString() {
        return String.format("ParameterConfig[%s, %s, %s]",
                getName(), getValue(), isExpand());
    }

    public static class Query {
        private final String name;
        private final String value;

        public Query(
                @JsonProperty("name") String name,
                @JsonProperty("value") String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    private static class BraceExpansion {

        public static List<String> expand(String s) {
            return expandRecursive("", s, "", new ArrayList<String>());
        }

        private static List<String> expandRecursive(String prefix, String s,
                                                    String suffix, List<String> dest) {
            // I used the code below as reference.
            //  http://rosettacode.org/wiki/Brace_expansion#Java
            int i1 = -1, i2 = 0;
            String noEscape = s.replaceAll("([\\\\]{2}|[\\\\][,}{])", "  ");
            StringBuilder sb = null;

            outer:
            while ((i1 = noEscape.indexOf('{', i1 + 1)) != -1) {
                i2 = i1 + 1;
                sb = new StringBuilder(s);
                for (int depth = 1; i2 < s.length() && depth > 0; i2++) {
                    char c = noEscape.charAt(i2);
                    depth = (c == '{') ? ++depth : depth;
                    depth = (c == '}') ? --depth : depth;
                    if (c == ',' && depth == 1) {
                        sb.setCharAt(i2, '\u0000');
                    } else if (c == '}' && depth == 0 && sb.indexOf("\u0000") != -1) {
                        break outer;
                    }
                }
            }

            if (i1 == -1) {
                if (suffix.length() > 0) {
                    expandRecursive(prefix + s, suffix, "", dest);
                } else {
                    final String out = String.format("%s%s%s", prefix, s, suffix).
                            replaceAll("[\\\\]{2}", "\\").replaceAll("[\\\\]([,}{])", "$1");
                    dest.add(out);
                }
            } else {
                for (String m : sb.substring(i1 + 1, i2).split("\u0000", -1)) {
                    expandRecursive(prefix + s.substring(0, i1), m, s.substring(i2 + 1) + suffix, dest);
                }
            }
            return dest;
        }
    }
}
