package org.genealogyarchive.dates;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the shared date fixtures in {@code src/test/resources/dates} (plain Java, no test framework). */
final class DateFixtures {

    record EnvelopeExample(String id, DateInterpretation interpretation, Long earliest, Long latest,
                           boolean overlaps1840to1850, boolean approximate, boolean uncertain) {
    }

    /** {@code expected} is empty when the parser must make no proposal. */
    record ParserExample(String expression, DateInterpretation expected) {
    }

    private DateFixtures() {
    }

    static List<EnvelopeExample> envelopeExamples() {
        List<EnvelopeExample> out = new ArrayList<>();
        for (String[] f : rows("/dates/envelope-examples.txt", 11)) {
            DateForm form = DateForm.valueOf(f[1]);
            DateInterpretation d = new DateInterpretation(form, CalendarSystem.valueOf(f[2]), YearStart.valueOf(f[3]),
                    point(f[4]), point(f[5]));
            out.add(new EnvelopeExample(f[0], d, number(f[6]), number(f[7]), Boolean.parseBoolean(f[8]),
                    Boolean.parseBoolean(f[9]), Boolean.parseBoolean(f[10])));
        }
        return out;
    }

    static List<ParserExample> parserExamples() {
        List<ParserExample> out = new ArrayList<>();
        for (String[] f : rows("/dates/parser-examples.txt", 4)) {
            DateInterpretation expected = f[1].equals("NONE") ? null
                    : DateInterpretation.of(DateForm.valueOf(f[1]), point(f[2]), point(f[3]));
            out.add(new ParserExample(f[0], expected));
        }
        return out;
    }

    /** YEAR[-MM[-DD]] with optional ;ABOUT ;ESTIMATED ;CALCULATED ;? ;dual=YEAR, or '-' for none. */
    static DatePoint point(String text) {
        if (text.equals("-")) {
            return null;
        }
        String[] parts = text.split(";");
        String[] ymd = parts[0].split("-");
        DatePoint p = new DatePoint(Integer.parseInt(ymd[0]), ymd.length > 1 ? Integer.parseInt(ymd[1]) : null,
                ymd.length > 2 ? Integer.parseInt(ymd[2]) : null, DateModifier.EXACT, false, null);
        for (int i = 1; i < parts.length; i++) {
            String flag = parts[i];
            if (flag.equals("?")) {
                p = p.asUncertain();
            } else if (flag.startsWith("dual=")) {
                p = p.withDualYear(Integer.parseInt(flag.substring(5)));
            } else {
                p = p.withModifier(DateModifier.valueOf(flag));
            }
        }
        return p;
    }

    private static Long number(String s) {
        return s.equals("-") ? null : Long.parseLong(s);
    }

    private static List<String[]> rows(String resource, int columns) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = DateFixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\|");
                if (f.length != columns) {
                    throw new IllegalStateException("Bad fixture line in " + resource + ": " + line);
                }
                for (int i = 0; i < f.length; i++) {
                    f[i] = f[i].trim();
                }
                rows.add(f);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
