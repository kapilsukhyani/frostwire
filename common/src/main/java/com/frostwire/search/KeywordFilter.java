/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created on 11/24/16.
 *
 * @author gubatron
 * @author aldenml
 */
public class KeywordFilter {
    private final boolean inclusive;
    private final String keyword;
    private final String stringForm;
    private static final String KEYWORD_FILTER_PATTERN = "(?is)(?<inclusive>\\+|-)?(:keyword:)(?<keyword>[^\\s-]*)";
    private KeywordDetector.Feature feature;

    public KeywordFilter(boolean inclusive, String keyword, KeywordDetector.Feature feature) {
        this(inclusive, keyword, (String) null);
        this.feature = feature;
    }

    /**
     * NOTE: If you use this constructor, make sure the stringForm passed matches the inclusive and keyword
     * parameters. The constructor performs no validations and this could lead to unwanted behavior when
     * asking for toString(), as the stringForm will be the one returned by toString().
     * @param inclusive - the keyword should be included or not
     * @param keyword - the keyword
     * @param stringForm - How this keyword filter was parsed out from a search
     */
    public KeywordFilter(boolean inclusive, String keyword, String stringForm) {
        this.inclusive = inclusive;
        this.keyword = keyword.toLowerCase();
        if (stringForm != null) {
            this.stringForm = stringForm;
        } else {
            this.stringForm = ((inclusive) ? "+":"-") + ":keyword:" + this.keyword;
        }
        this.feature = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof KeywordFilter)) {
            return false;
        } else {
            KeywordFilter other = (KeywordFilter) obj;
            return inclusive == other.inclusive && keyword.equals(other.keyword);
        }
    }

    @Override
    public int hashCode() {
        return keyword.hashCode() * (inclusive ? 1 : -1);
    }

    public boolean isInclusive() {
        return inclusive;
    }

    public String getKeyword() {
        return keyword;
    }

    public KeywordDetector.Feature getFeature() {
        return this.feature;
    }

    @Override
    public String toString() {
        return stringForm;
    }

    /**
     * @param searchTerms must match [+,-]:keyword:<theKeyword>
     * @return The list of KeywordFilter objects found in the query string.
     */
    public static List<KeywordFilter> parseKeywordFilters(String searchTerms) {
        List<KeywordFilter> pipeline = new LinkedList<>();
        Pattern pattern = Pattern.compile(KEYWORD_FILTER_PATTERN);
        Matcher matcher = pattern.matcher(searchTerms);
        while (matcher.find()) {
            boolean inclusive = true;
            String inclusiveMatch = matcher.group("inclusive");
            if (inclusiveMatch != null && inclusiveMatch.equals("-")) {
                inclusive = false;
            }
            String keyword = matcher.group("keyword");
            if (keyword != null) {
                pipeline.add(new KeywordFilter(inclusive, keyword, matcher.group(0)));
            }
        }
        return pipeline;
    }

    public boolean accept(final String lowercaseHaystack) {
        boolean found = lowercaseHaystack.contains(keyword);
        return ((inclusive && found) || (!inclusive && !found));
    }

    private static String getSearchResultHaystack(SearchResult sr) {
        StringBuilder queryString = new StringBuilder();
        if (sr.getSource() == null) {
            System.err.println("WARNING: " + sr.getClass().getSimpleName() + " has no source!");
        } else {
            queryString.append(sr.getSource());
            queryString.append(" ");
        }
        queryString.append(sr.getDisplayName());
        queryString.append(" ");
        if (sr instanceof FileSearchResult) {
            queryString.append(" ");
            queryString.append(((FileSearchResult) sr).getFilename());
        }
        queryString.append(" ");
        queryString.append(sr.getDetailsUrl());
        queryString.append(" ");
        queryString.append(sr.getThumbnailUrl());
        if (sr.getLicense() != Licenses.UNKNOWN) {
            queryString.append(sr.getLicense().getName());
        }
        return queryString.toString().toLowerCase();
    }

    public static boolean passesFilterPipeline(final SearchResult sr, final List<KeywordFilter> filterPipeline) {
        if (filterPipeline == null || filterPipeline.size() == 0) {
            return true;
        }
        String haystack = getSearchResultHaystack(sr);
        // Group Filters by Feature so we can make the following search.
        // or by feature, and by different feature.
        Map<KeywordDetector.Feature, List<KeywordFilter>>  featureFilters = new HashMap<>();
        Iterator<KeywordFilter> it = filterPipeline.iterator();
        while (it.hasNext()) {
            KeywordFilter filter = it.next();
            List<KeywordFilter> filters = featureFilters.get(filter.feature);
            if (filters == null) {
                filters = new LinkedList<>();
                featureFilters.put(filter.feature, filters);
            }
            filters.add(filter);
        }
        // now depending on the features that we have we'll have N Feature conditions we'll AND.
        Set<KeywordDetector.Feature> features = featureFilters.keySet();

        List<List<Boolean>> conditionsPerFeature = new LinkedList<>();
        for (KeywordDetector.Feature feature : features) {
            List<Boolean> featureFilterResults = new LinkedList<>();
            List<KeywordFilter> keywordFilters = featureFilters.get(feature);

            for (KeywordFilter filter : keywordFilters) {
                featureFilterResults.add(filter.accept(haystack));
            }
            conditionsPerFeature.add(featureFilterResults);
        }

        // evaluate logic circuit
        boolean result = true;
        for (List<Boolean> featureResults : conditionsPerFeature) {
            boolean featureResult = false;
            for (Boolean fr : featureResults) {
                featureResult = featureResult || fr;
            }
            result = result && featureResult;
        }
        conditionsPerFeature.clear();
        return result;
    }

    public static String cleanQuery(String query, List<KeywordFilter> keywordFilters) {
        for (KeywordFilter filter : keywordFilters) {
            query = query.replace(filter.toString(), "");
        }
        return query.trim();
    }

    @SuppressWarnings("unused")
    private static class KeywordFilterTests {
        private static final FileSearchResult fsr = new FileSearchResult() {
            @Override
            public String getFilename() {
                return "timon_of_athens.txt";
            }

            @Override
            public long getSize() {
                return 0;
            }

            @Override
            public String getDisplayName() {
                return "Timon of Athens";
            }

            @Override
            public String getDetailsUrl() {
                return "http://shakespeare.mit.edu/timon/timon.4.1.html";
            }

            @Override
            public long getCreationTime() {
                return 0;
            }

            @Override
            public String getSource() {
                return "MIT";
            }

            @Override
            public License getLicense() {
                return Licenses.PUBLIC_DOMAIN_MARK;
            }

            @Override
            public String getThumbnailUrl() {
                return "Let me look back upon thee. O thou wall, That girdlest in those wolves, dive in the earth";
            }

            @Override
            public int uid() {
                return 0;
            }
        };

        // Poor man's JUnit, Temporary until we formalize unit tests in Android
        // I tried with compileTest '...junit' on build.gradle but failed.
        // just needed to get simple tests going.

        public static boolean assertTrue(String description, boolean result) {
            PrintStream ops = result ? System.out : System.err;
            ops.println((result ? "PASSED" : "FAILED") + " [" + description + "]");
            return result;
        }

        public static boolean assertFalse(String description, boolean result) {
            PrintStream ops = !result ? System.out : System.err;
            ops.println((!result ? "PASSED" : "FAILED") + " [" + description + "]");
            return !result;
        }

        private static boolean testInclusiveFilters() {
            final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
            KeywordFilter MITfilter = new KeywordFilter(true, "MIT", (KeywordDetector.Feature) null);
            if (!assertTrue("'MIT' keyword inclusion test", MITfilter.accept(haystack))) {
                return false;
            }

            KeywordFilter notthereFilter = new KeywordFilter(true, "notthere", (KeywordDetector.Feature) null);
            if (!assertFalse("'notthere' keyword inclusion fail test", notthereFilter.accept(haystack))) {
                return false;
            }

            KeywordFilter athensFilter = new KeywordFilter(true, "athens", (KeywordDetector.Feature) null);
            if (!assertTrue("'athens' keyword inclusion test", athensFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> acceptablePipeline = new LinkedList<>();
            acceptablePipeline.add(MITfilter);
            acceptablePipeline.add(athensFilter);
            if (!assertTrue("inclusion pipeline test", KeywordFilter.passesFilterPipeline(fsr, acceptablePipeline))) {
                return false;
            }

            List<KeywordFilter> failPipeline = new LinkedList<>();
            failPipeline.add(MITfilter);
            failPipeline.add(notthereFilter);
            failPipeline.add(athensFilter); // this one shouldn't even be checked as it should short circuit
            //noinspection RedundantIfStatement
            if (!assertFalse("inclusion pipeline fail test", KeywordFilter.passesFilterPipeline(fsr, failPipeline))) {
                return false;
            }
            return true;
        }

        private static boolean testExclusiveFilters() {
            final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
            KeywordFilter MITfilter = new KeywordFilter(false, "MIT", (KeywordDetector.Feature) null);
            if (!assertFalse("'MIT' keyword exclusion fail test", MITfilter.accept(haystack))) {
                return false;
            }

            KeywordFilter notthereFilter = new KeywordFilter(false, "notthere", (KeywordDetector.Feature) null);
            if (!assertTrue("'notthere' keyword exclusion test", notthereFilter.accept(haystack))) {
                return false;
            }

            KeywordFilter frostwireFilter = new KeywordFilter(false, "frostwire", (KeywordDetector.Feature) null);
            if (!assertTrue("'frostwire' keyword exclusion test", notthereFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> acceptablePipeline = new LinkedList<>();
            acceptablePipeline.add(notthereFilter);
            acceptablePipeline.add(frostwireFilter);
            if (!assertTrue("exclusion pipeline test", passesFilterPipeline(fsr,acceptablePipeline))) {
                return false;
            }

            KeywordFilter athensFilter = new KeywordFilter(false, "athens", (KeywordDetector.Feature) null);
            if (!assertFalse("'athens' exclusion fail test", athensFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> failPipeline = new LinkedList<>();
            failPipeline.add(frostwireFilter);
            failPipeline.add(athensFilter);
            failPipeline.add(MITfilter);
            failPipeline.add(notthereFilter);
            //noinspection RedundantIfStatement
            if (!assertFalse("exclusion pipeline fail test", passesFilterPipeline(fsr, failPipeline))) {
                return false;
            }
            return true;
        }

        private static boolean testMixedFilters() {
            KeywordFilter MITfilter = new KeywordFilter(true, "MIT", (KeywordDetector.Feature) null);
            KeywordFilter frostwireExclusionFilter = new KeywordFilter(false, "frostwire", (KeywordDetector.Feature) null);
            KeywordFilter athensFilter = new KeywordFilter(true, "athens", (KeywordDetector.Feature) null);
            List<KeywordFilter> mixedPipeline = new LinkedList<>();
            mixedPipeline.add(MITfilter);
            mixedPipeline.add(frostwireExclusionFilter);
            mixedPipeline.add(athensFilter);
            //noinspection RedundantIfStatement
            if (!assertTrue("mixed pipeline test", passesFilterPipeline(fsr, mixedPipeline))) {
                return false;
            }
            return true;
        }

        private static boolean testParseKeywordFilters() {
            //parseKeywordFilters
            List<KeywordFilter> keywordFilters = parseKeywordFilters("yaba daba doo +:keyword:thein -:keyword:theout +:keyward:notamatch :keyword:home");
            if (!assertTrue("parse keywords detection test 1", keywordFilters.size() == 3)) return false;
            if (!assertTrue("parse keywords detection test 2", keywordFilters.get(0).inclusive)) return false;
            if (!assertFalse("parse keywords detection test 3",keywordFilters.get(1).inclusive)) return false;
            if (!assertTrue("parse keywords detection test 4", keywordFilters.get(2).inclusive)) return false;
            if (!assertTrue("parse keywords detection test 5", keywordFilters.get(2).keyword.equals("home"))) return false;
            if (!assertTrue("toString() test 1", keywordFilters.get(0).toString().equals("+:keyword:thein"))) return false;
            if (!assertTrue("toString() test 2", keywordFilters.get(1).toString().equals("-:keyword:theout"))) return false;
            //noinspection RedundantIfStatement
            if (!assertTrue("toString() test 3", keywordFilters.get(2).toString().equals(":keyword:home"))) return false;
            return true;
        }

        private static boolean testConstructors() {
            KeywordFilter f = new KeywordFilter(true, "wisdom", (KeywordDetector.Feature) null);
            if (!assertTrue("constructor test 1", f.inclusive)) return false;
            if (!assertTrue("constructor test 2", f.keyword.equals("wisdom"))) return false;
            if (!assertTrue("constructor test 3", f.toString().equals("+:keyword:wisdom"))) return false;
            f = new KeywordFilter(false, "patience", (KeywordDetector.Feature) null);
            if (!assertFalse("constructor test 4", f.inclusive)) return false;
            if (!assertTrue("constructor test 5", f.keyword.equals("patience"))) return false;
            if (!assertTrue("constructor test 6", f.toString().equals("-:keyword:patience"))) return false;
            f = new KeywordFilter(true, "love", ":keyword:love");
            if (!assertTrue("constructor test 7", f.inclusive)) return false;
            if (!assertTrue("constructor test 8", f.keyword.equals("love"))) return false;
            if (!assertFalse("constructor test 9", f.toString().equals("+:keyword:love"))) return false;
            //noinspection RedundantIfStatement
            if (!assertTrue("constructor test 9", f.toString().equals(":keyword:love"))) return false;
            return true;
        }

        private static boolean testCleanQuery() {
            String query = "I know it is wet and the sun is not sunny, but we can have lots of good fun that is funny :keyword:somesource -:keyword:mp4 +:keyword:pdf";
            List<KeywordFilter> keywordFilters = parseKeywordFilters(query);
            if (!assertTrue("test cleanQuery 1", keywordFilters.size() == 3)) return false;
            String cleaned = cleanQuery(query, keywordFilters);
            //noinspection RedundantIfStatement
            if (!assertTrue("test cleanQuery 2",
                    cleaned.equals("I know it is wet and the sun is not sunny, but we can have lots of good fun that is funny")))
                return false;
            return true;
        }

        public static void main(String[] args) {
            if (!KeywordFilterTests.testInclusiveFilters()) return;
            if (!KeywordFilterTests.testExclusiveFilters()) return;
            if (!KeywordFilterTests.testMixedFilters()) return;
            if (!KeywordFilterTests.testParseKeywordFilters()) return;
            if (!KeywordFilterTests.testConstructors()) return;
            if (!KeywordFilterTests.testCleanQuery()) return;
            System.out.println("PASSED ALL TESTS");
        }
    }
}
