package com.businessplatform.hr.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cambodia's official public holidays, as set each year by sub-decree, for HR to copy into an
 * organisation in one go. Several follow the lunar calendar, so each year is added by hand from its
 * sub-decree; years not listed here are added day by day in HR settings.
 * <ul>
 * <li>2026: Sub-Decree No. 167 (21 days; Labour Day and Visak Bochea fall together on 1 May).</li>
 * <li>2027: Sub-Decree No. 198 ANKr.BK (22 days).</li>
 * </ul>
 */
public final class CambodiaHolidays {

    public record Day(LocalDate date, String nameEn, String nameKm) {
    }

    private static final String NEW_YEAR = "International New Year Day|ទិវាចូលឆ្នាំសកល";
    private static final String GENOCIDE = "Victory over Genocide Day|ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍";
    private static final String WOMEN = "International Women's Day|ទិវានារីអន្តរជាតិ";
    private static final String KHMER_NEW_YEAR = "Khmer New Year|ពិធីបុណ្យចូលឆ្នាំថ្មីប្រពៃណីជាតិ";
    private static final String LABOUR = "International Labour Day|ទិវាពលកម្មអន្តរជាតិ";
    private static final String VISAK = "Visak Bochea Day|ពិធីបុណ្យវិសាខបូជា";
    private static final String PLOUGHING = "Royal Ploughing Ceremony|ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល";
    private static final String KING = "His Majesty the King's Birthday|ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម ព្រះមហាក្សត្រ";
    private static final String QUEEN_MOTHER = "Her Majesty the Queen Mother's Birthday"
            + "|ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម សម្ដេចព្រះមហាក្សត្រី ព្រះវររាជមាតា";
    private static final String CONSTITUTION = "Constitution Day|ទិវាប្រកាសរដ្ឋធម្មនុញ្ញ";
    private static final String PCHUM_BEN = "Pchum Ben Festival|ពិធីបុណ្យភ្ជុំបិណ្ឌ";
    private static final String KING_FATHER = "Commemoration Day of King Father Norodom Sihanouk"
            + "|ទិវាប្រារព្ធពិធីគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះបរមរតនកោដ្ឋ";
    private static final String CORONATION = "His Majesty the King's Coronation Day|ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ";
    private static final String INDEPENDENCE = "Independence Day|ពិធីបុណ្យឯករាជ្យជាតិ";
    private static final String WATER = "Water Festival|ព្រះរាជពិធីបុណ្យអុំទូក បណ្ដែតប្រទីប និងសំពះព្រះខែ អកអំបុក";
    private static final String PEACE = "Peace Day in Cambodia|ទិវាសន្តិភាពនៅកម្ពុជា";

    private static final Map<Integer, List<Day>> YEARS = Map.of(
            2026, days(2026, "01-01", NEW_YEAR, "01-07", GENOCIDE, "03-08", WOMEN, "04-14", KHMER_NEW_YEAR,
                    "04-15", KHMER_NEW_YEAR, "04-16", KHMER_NEW_YEAR,
                    "05-01", "International Labour Day and Visak Bochea Day|ទិវាពលកម្មអន្តរជាតិ និងពិធីបុណ្យវិសាខបូជា",
                    "05-05", PLOUGHING, "05-14", KING, "06-18", QUEEN_MOTHER, "09-24", CONSTITUTION,
                    "10-10", PCHUM_BEN, "10-11", PCHUM_BEN, "10-12", PCHUM_BEN, "10-15", KING_FATHER,
                    "10-29", CORONATION, "11-09", INDEPENDENCE, "11-23", WATER, "11-24", WATER, "11-25", WATER,
                    "12-29", PEACE),
            2027, days(2027, "01-01", NEW_YEAR, "01-07", GENOCIDE, "03-08", WOMEN, "04-14", KHMER_NEW_YEAR,
                    "04-15", KHMER_NEW_YEAR, "04-16", KHMER_NEW_YEAR, "05-01", LABOUR, "05-14", KING,
                    "05-20", VISAK, "05-24", PLOUGHING, "06-18", QUEEN_MOTHER, "09-24", CONSTITUTION,
                    "09-29", PCHUM_BEN, "09-30", PCHUM_BEN, "10-01", PCHUM_BEN, "10-15", KING_FATHER,
                    "10-29", CORONATION, "11-09", INDEPENDENCE, "11-12", WATER, "11-13", WATER, "11-14", WATER,
                    "12-29", PEACE));

    private CambodiaHolidays() {
    }

    /** The official days of {@code year}, or an empty list when that year isn't built in. */
    public static List<Day> of(int year) {
        return YEARS.getOrDefault(year, List.of());
    }

    public static List<Integer> years() {
        return YEARS.keySet().stream().sorted().toList();
    }

    private static List<Day> days(int year, String... pairs) {
        List<Day> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String[] names = pairs[i + 1].split("\\|", 2);
            out.add(new Day(LocalDate.parse(year + "-" + pairs[i]), names[0], names[1]));
        }
        return List.copyOf(out);
    }
}
