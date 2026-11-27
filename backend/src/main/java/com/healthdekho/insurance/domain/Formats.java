package com.healthdekho.insurance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates and amounts inside user-facing messages, in the same style the web app shows them. */
public final class Formats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private Formats() {
    }

    public static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** "1 year", "20 years": also the stored form of a preferred term (spec sample 5.5.6). */
    public static String years(int count) {
        return count == 1 ? "1 year" : count + " years";
    }

    /** ₹2,85,000.50. The JDK's en-IN locale groups in thousands, so the lakh grouping is done here. */
    public static String rupees(BigDecimal amount) {
        String plain = Money.normalize(amount).abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        StringBuilder grouped = new StringBuilder();
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole.substring(whole.length() - 3));
        } else {
            grouped.append(whole);
        }
        return (amount.signum() < 0 ? "-₹" : "₹") + grouped + plain.substring(dot);
    }
}
