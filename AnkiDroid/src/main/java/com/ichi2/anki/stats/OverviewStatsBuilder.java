/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@m-goldbach.net>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.stats;


import android.database.Cursor;
import android.text.TextUtils;
import android.webkit.WebView;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OverviewStatsBuilder {
    private static final int CARDS_INDEX = 0;
    private static final int THETIME_INDEX = 1;
    private static final int FAILED_INDEX = 2;
    private static final int LRN_INDEX = 3;
    private static final int REV_INDEX = 4;
    private static final int RELRN_INDEX = 5;
    private static final int FILT_INDEX = 6;
    private static final int MCNT_INDEX = 7;
    private static final int MSUM_INDEX = 8;
    
    private final Collection mCol;
    private final boolean mWholeCollection;
    private final Stats.AxisType mType;

    private OverviewStats mOStats;

    // TODO:
    String colYoung = "#7c7";
    String colMature = "#070";
    String colCum = "rgba(0,0,0,0.9)";
    String colLearn = "#00F";
    String colRelearn = "#c00";
    String colCram = "#ff0";
    String colIvl = "#077";
    String colHour = "#ccc";
    String colTime = "#770";
    String colUnseen = "#000";
    String colSusp = "#ff0";

    // TODO: we should really have our own thing
    String mCSS = "<style>\n" +
                "h1 { margin-bottom: 0; margin-top: 1em; }\n" +
                ".pielabel { text-align:center; padding:0px; color:white; }\n" +
                "body {background-image: url(data:image/png;base64,%s); }\n" +
                "</style>";

    public class OverviewStats {
        public int forecastTotalReviews;
        public double forecastAverageReviews;
        public int forecastDueTomorrow;
        public double reviewsPerDayOnAll;
        public double reviewsPerDayOnStudyDays;
        public double reviewsTotalMinutes;
        public double reviewsAverageMinutes;
        public int allDays;
        public int daysStudied;
        public double timePerDayOnAll;
        public double timePerDayOnStudyDays;
        public double totalTime;
        public int totalReviews;
        public double newCardsPerDay;
        public int totalNewCards;
        public double averageInterval;
        public double longestInterval;
    }

    public OverviewStatsBuilder(WebView chartView, Collection collectionData, boolean isWholeCollection, Stats.AxisType mStatType) {
        mWebView = chartView;
        mCol = collectionData;
        mWholeCollection = isWholeCollection;
        mType = mStatType;
        mOStats = new OverviewStats();
    }

    public String report() {
        String txt = mCSS;
        txt += todayStats();
        txt += dueGraph();
        txt += repsGraph();
        txt += introductionGraph();
        txt += ivlGraph();
        txt += hourGraph();
        txt += easeGraph();
        txt += cardGraph();
        txt += footer();
        return txt; // TODO: do we need to also do some centering?
    }


    /**
     * Today stats
     * ***********************************************************
     */

    private String todayStats() {
        String b = _title("Today");
        // studied today
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lim = " and " + lim;
        }

        Cursor cur = null;
        Cursor cur2 = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(
                    "select count(), sum(time)/1000,\n" +
                    "sum(case when ease = 1 then 1 else 0 end), /* failed */\n" +
                    "sum(case when type = 0 then 1 else 0 end), /* learning */\n" +
                    "sum(case when type = 1 then 1 else 0 end), /* review */\n" +
                    "sum(case when type = 2 then 1 else 0 end), /* relearn */\n" +
                    "sum(case when type = 3 then 1 else 0 end) /* filter */\n" +
                    "from revlog where id > ? " + lim,
                    new String[]{Long.toString((mCol.getSched().getDayCutoff()-86400)*1000)});
            if (cur.moveToFirst()) {
                // TODO: check if we get a 0 instead of a null when no value
                int cards = cur.getInt(0);
                double thetime = cur.getDouble(1);
                int failed = cur.getInt(2);
                int lrn = cur.getInt(3);
                int rev = cur.getInt(4);
                int relrn = cur.getInt(5);
                int filt = cur.getInt(6);

                // studied
                String msgp1 = String.format(Locale.getDefault(), "<!--studied-->%d cards", cards);
                b += "<br>" + String.format("Studied %s in %ss today.",
                        bold(msgp1), bold(Utils.fmtTimeSpan(thetime, 1)));
                // again/pass count
                b += "<br>" + String.format("Again count: %s", bold(failed));
                if (cards > 0) {
                    b += " " + String.format("(%s correct)",bold(
                            String.format(Locale.getDefault(), "%0.1f%%", ((1-failed/(float)cards)*100))));
                }
                // type breakdown
                b += "<br>";
                b += String.format("Learn: %s, Review: %s, Relearn: %s, Filtered: %s",
                        bold(lrn), bold(rev), bold(relrn), bold(filt));
                // mature today
                cur2 = mCol.getDb().getDatabase().rawQuery(
                            "select count(), sum(case when ease = 1 then 0 else 1 end) from revlog\n" +
                            "where lastIvl >= 21 and id > ?" + lim,
                        new String[]{Long.toString((mCol.getSched().getDayCutoff()-86400)*1000)});
                if (cur2.moveToFirst()) {
                    b += "<br>";
                    int mcnt = cur2.getInt(0);
                    int msum = cur2.getInt(1);
                    if (mcnt > 0) {
                        b += String.format(Locale.getDefault(), "Correct answers on mature cards: %d/%d (%.1f%%)",
                                msum, mcnt, (msum / (float) mcnt * 100));
                    } else {
                        b += "No mature cards were studied today.";
                    }
                    return b;
                }
            }
            return "";
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
            if (cur2 != null && !cur2.isClosed()) {
                cur2.close();
            }
        }
    }


    /**
     * Due and cumulative due
     * ***********************************************************
     */

    private String dueGraph() {
        Integer start = null;
        Integer end = null;
        Integer chunk = 0;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            start = 0; end = 31; chunk = 1;
        } else if (mType == Stats.AxisType.TYPE_YEAR) {
            start = 0; end = 52; chunk = 7;
        } else if (mType == Stats.AxisType.TYPE_LIFE) {
            start = 0; end = null; chunk = 30;
        }
        List<int[]> d = _due(start, end, chunk);
        List<int[]> yng = new ArrayList<>();
        List<int[]> mtr = new ArrayList<>();
        int tot = 0;
        List<int[]> totd = new ArrayList<>();
        for (int[] day : d) {
            yng.add(new int[] {day[0], day[1]});
            mtr.add(new int[] {day[0], day[2]});
            tot += day[1]+day[2];
            totd.add(new int[] {day[0], tot});
        }
        // TODO: data is skipped because we aren't building the graph

        String txt = _title("Forecast", "The number of reviews due in the future.");

        // TODO: axis/graph data is skipped

        txt += _dueInfo(tot, totd.size() * chunk);
        return txt;
    }


    private String _dueInfo(int tot, int num) {
        List<String> i = new ArrayList<>();
        _line(i, "Total", String.format(Locale.getDefault(), "%d reviews", tot));
        _line(i, "Average", _avgDay(tot, num "reviews"));
        int tom = mCol.getDb().queryScalar(String.format(Locale.US,
                    "select count() from cards where did in %s and queue in (2,3) " +
                    "and due = ?", _limit()), new String[]{Integer.toString(mCol.getSched().getToday() + 1)});
        String tomorrow = String.format(Locale.getDefault(), "%d card", tom);
        _line(i, "Due tomorrow", tomorrow);
        return _lineTbl(i);
    }


    private List<int[]> _due(Integer start, Integer end, int chunk) {
        String lim = "";
        if (start != null) {
            lim += String.format(Locale.US, " and due-%d >= %d", mCol.getSched().getToday(), start);
        }
        if (end != null) {
            lim += String.format(Locale.US, " and day < %d", end);
        }

        List<int[]> d = new ArrayList<>();
        Cursor cur = null;
        try {
            String query;
            query = String.format(Locale.US,
                    "select (due-%d)/%d as day,\n" +
                    "sum(case when ivl < 21 then 1 else 0 end), -- yng\n" +
                    "sum(case when ivl >= 21 then 1 else 0 end) -- mtr\n" +
                    "from cards\n" +
                    "where did in %s and queue in (2,3)\n" +
                    "%s\n" +
                    "group by day order by day",
                    mCol.getSched().getToday(), chunk, _limit(), lim);
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                d.add(new int[]{cur.getInt(0), cur.getInt(1), cur.getInt(2)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return d;
    }


    /**
     * Added, reps and time spent
     * ***********************************************************
     */

    private String introductionGraph() {
        Integer days = null;
        Integer chunk = 0;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            days = 30; chunk = 1;
        } else if (mType == Stats.AxisType.TYPE_YEAR) {
            days = 52; chunk = 7;
        } else if (mType == Stats.AxisType.TYPE_LIFE) {
            days = null; chunk = 30;
        }
        return _introductionGraph(_added(days, chunk), days, "Added");
    }

    private String _introductionGraph(List<int[]> data, int days, String title) {
        if (data == null || data.size() == 0) {
            return "";
        }
        List<int[]> d = data;
        // TODO: skipped conf dict and related graph code
        //graph
        String txt = "";
        // total and per day average
        int tot = 0;
        for (int[] i : d) {
            tot += i[1];
        }
        Integer period = _periodDays();
        if (period == null) {
            // base off date of earliest card
            period = _deckAge("add");
        }
        List<String> i = new ArrayList<>();
        _line(i, "Total", String.format(Locale.getDefault(), "%d cards", tot));
        _line(i, "Average", _avgDay(tot, period, "cards"));
        txt += _lineTbl(i); // TODO: do we really want to table?
        return txt;
    }


    private String repsGraph() {
        Integer days = null;
        Integer chunk = 0;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            days = 30; chunk = 1;
        } else if (mType == Stats.AxisType.TYPE_YEAR) {
            days = 52; chunk = 7;
        } else if (mType == Stats.AxisType.TYPE_LIFE) {
            days = null; chunk = 30;
        }
        return _repsGraph(_done(days, chunk), days, "Review Count", "Review Time");
    }


    private String _repsGraph(List<double[]> data, Integer days, String reptitle, String timetitle) {
        if (data == null || data.size() == 0) {
            return "";
        }
        List<double[]> d = data;
        // TODO: skipped charting and graphing stuff here
        // conf = dict()
        if (days != null) {
            // conf[]
        }

        // reps
        ArrayList<Object[]> spec = new ArrayList<>();
        spec.add(new Object[]{3, colMature, "Mature"});
        spec.add(new Object[]{2, colYoung, "Young"});
        spec.add(new Object[]{4, colRelearn, "Relearn"});
        spec.add(new Object[]{1, colLearn, "Learn"});
        spec.add(new Object[]{5, colCram, "Cram"});
        Object[] srd1 = _splitRepData(d, spec);
        Object repdata = srd1[0];
        List<double[]> repsum = (List<double[]>) srd1[1];

        String txt = _title(reptitle, "The number of questions you have answered.");
        // TODO: txt += plot stuff

        int[] ds = _daysStudied();
        int daysStud = ds[0];
        int fstDay = ds[1];
        Object[] ai1 = _ansInfo(repsum, daysStud, fstDay, "reviews");
        String rep = (String) ai1[0];
        int tot = (int) ai1[1];
        txt += rep;

        // time
        spec = new ArrayList<>();
        spec.add(new Object[]{8, colMature, "Mature"});
        spec.add(new Object[]{7, colYoung, "Young"});
        spec.add(new Object[]{9, colRelearn, "Relearn"});
        spec.add(new Object[]{6, colLearn, "Learn"});
        spec.add(new Object[]{10, colCram, "Cram"});
        Object[] srd2 = _splitRepData(d, spec);
        Object timdata = srd2[0];
        List<double[]> timsum =  (List<double[]>) srd2[1];
        String t;
        boolean convHours;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            t = "Minutes";
            convHours = false;
        } else {
            t = "Hours";
            convHours = true;
        }
        txt += _title(timetitle, "The time taken to answer the questions.");
        // TODO: txt += plot stuff
        Object[] ai2 = _ansInfo(timsum, daysStud, fstDay, "minutes", convHours, tot);
        rep = (String) ai2[0];
        int tot2 = (int) ai2[1];
        txt += rep;
        return txt;
    }


    private Object[] _ansInfo(List<double[]> totd, int studied, int first, String unit) {
        return _ansInfo(totd, studied, first, unit, false, null);
    }

    private Object[] _ansInfo(List<double[]> totd, int studied, int first, String unit, boolean convHours,
                              Integer total) {
        if (totd == null || totd.size() == 0) {
            return null;
        }
        double tot = totd.get(totd.size()-1)[1];
        Integer period = _periodDays();
        if (period == null) {
            // base off earliest repetition date
            period = _deckAge('review');
        }
        List<String> i = new ArrayList<>();
        _line(i, "Days studied",
                String.format(Locale.getDefault(),"<b>%d%%</b> (%s of %s)",
                        studied / (float) period * 100, studied, period), false);
        String tunit;
        if (convHours) {
            tunit = "hours";
        } else {
            tunit = unit;
        }
        _line(i, "Total", String.format(Locale.getDefault(), "%s %s", (int) tot, tunit));
        if (convHours) {
            // convert to minutes
            tot *= 60;
        }
        _line(i, "Average for days studied", _avgDay(tot, studied, unit));
        if (studied != period) {
            // don't display if you did study every day
            _line(i, "If you studied every day", _avgDay(
                    tot, period, unit));
        }
        if (total != null && total != 0 && tot != 0) {
            float perMin = total / (float) tot;
            perMin = (float) Math.round(perMin); // FIXME
            // don't round down to zero
            String text;
            if (perMin < 0.1) {
                text = "less than 0.1 cards/minute";
            } else {
                text = String.format(Locale.getDefault(), "%.01f cards/minute", perMin);
            }
            _line(i, "Average answer time",
                    String.format(Locale.getDefault(), "%0.1fs (%s)", (tot*60)/total, text);
        }
        return new Object[]{_lineTbl(i), (int) tot}; // TODO: do we tablize?
    }

    private Object[] _splitRepData(List<double[]> data, List<Object[]> spec) {
        Map<Integer, List<double[]>> sep = new HashMap();
        Map<Integer, Double> totcnt = new HashMap();
        Map<Integer, List<double[]>> totd = new HashMap();
        List<double[]> alltot = new ArrayList<>();
        int allcnt = 0;
        for (Object[] s : spec) {
            Integer n = (Integer) s[0];
            totcnt.put(n, 0.0);
            totd.put(n, new ArrayList<double[]>());
        }
        List<Integer> sum = new ArrayList<>();
        for (double[] row : data) {
            for (Object[] s : spec) {
                Integer n = (Integer) s[0];
                if (!sep.containsKey(n)) {
                    sep.put(n, new ArrayList<double[]>());
                }
                sep.get(n).add(new double[]{row[0], row[n]});
                totcnt.put(n, totcnt.get(n) + row[n]);
                allcnt += row[n];
                totd.get(n).add(new double[]{row[0], totcnt.get(n)});
            }
            alltot.add(new double[]{row[0], allcnt});
        }
        List<Object[]> ret = new ArrayList<>();
        for (Object[] s : spec) {
            Integer n = (Integer) s[0];
            String col = (String) s[1];
            String lab = (String) s[2];
            if (totd.get(n).size() > 0 && totcnt.containsKey(n)) {
                // bars
                ret.add(null);
                // lines
                ret.add(null);
            }
        }
        return new Object[] {ret, alltot};
    }


    private List<int[]> _added() {
        return _added(7, 1);
    }

    private List<int[]> _added(Integer num, Integer chunk) {
        List<String> lims = new ArrayList<>();
        if (num != null) {
            lims.add(String.format(Locale.US, "id > %d",
                    (mCol.getSched().getDayCutoff()-(num*chunk*86400))*1000));
        }
        lims.add(String.format(Locale.US, "did in %s", _limit()));
        String lim;
        if (lims.size() > 0) {
            lim = "where " + TextUtils.join(" and ", lims);
        } else {
            lim = "";
        }
        double tf;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            tf = 60.0; // minutes
        } else {
            tf = 3600.0; // hours
        }
        Cursor cur = null;
        List<int[]> d = new ArrayList<>();
        try {
            String query;
            query = String.format(Locale.US,
                    "select\n" +
                    "(cast((id/1000.0 - %d) / 86400.0 as int))/%d as day,\n" +
                    "count(id)\n" +
                    "from cards %s\n" +
                    "group by day order by day",
                    mCol.getSched().getDayCutoff(), chunk, lim);
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                d.add(new int[]{cur.getInt(0), cur.getInt(1)});
            }
            return d;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    private List<double[]> _done(Integer num, Integer chunk) {
        List<String> lims = new ArrayList<>();
        if (num != null) {
            lims.add(String.format(Locale.US, "id > %d",
                    (mCol.getSched().getDayCutoff()-(num*chunk*86400))*1000));
        }
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lims.add(lim);
        }
        if (lims.size() > 0) {
            lim = "where " + TextUtils.join(" and ", lims);
        } else {
            lim = "";
        }
        double tf;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            tf = 60.0; // minutes
        } else {
            tf = 3600.0; //hours
        }
        String query = String.format(Locale.US,
                "select\n" +
                        "(cast((id/1000.0 - %d) / 86400.0 as int))/%d as day,\n" +
                        "sum(case when type = 0 then 1 else 0 end), -- lrn count\n" +
                        "sum(case when type = 1 and lastIvl < 21 then 1 else 0 end), -- yng count\n" +
                        "sum(case when type = 1 and lastIvl >= 21 then 1 else 0 end), -- mtr count\n" +
                        "sum(case when type = 2 then 1 else 0 end), -- lapse count\n" +
                        "sum(case when type = 3 then 1 else 0 end), -- cram count\n" +
                        "sum(case when type = 0 then time/1000.0 else 0 end)/%f, -- lrn time\n" +
                        "-- yng + mtr time\n" +
                        "sum(case when type = 1 and lastIvl < 21 then time/1000.0 else 0 end)/%f,\n" +
                        "sum(case when type = 1 and lastIvl >= 21 then time/1000.0 else 0 end)/%f,\n" +
                        "sum(case when type = 2 then time/1000.0 else 0 end)/%f, -- lapse time\n" +
                        "sum(case when type = 3 then time/1000.0 else 0 end)/%f -- cram time\n" +
                        "from revlog %s\n" +
                        "group by day order by day",
                mCol.getSched().getDayCutoff(), chunk, tf, tf, tf, tf, tf, lim);
        List<double[]> result = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                result.add(new double[]{
                        cur.getDouble(0), cur.getDouble(1), cur.getDouble(2),
                        cur.getDouble(3), cur.getDouble(4), cur.getDouble(5),
                        cur.getDouble(6), cur.getDouble(7), cur.getDouble(8),
                        cur.getDouble(9), cur.getDouble(10)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return result;
    }


    private int[] _daysStudied() {
        List<String> lims = new ArrayList<>();
        Integer num = _periodDays();
        if (num != null) {
            lims.add(String.format(Locale.US,
                    "id > %d",
                    ((mCol.getSched().getDayCutoff()-(num*86400))*1000)));
        }
        String rlim = _revlogLimit();
        String lim;
        if (!TextUtils.isEmpty(rlim)) {
            lims.add(rlim);
        }
        if (lims.size() > 0) {
            lim = "where " + TextUtils.join(" and ", lims);
        } else {
            lim = "";
        }
        String query = String.format(Locale.US,
                "select count(), abs(min(day)) from (select\n" +
                        "(cast((id/1000 - %d) / 86400.0 as int)+1) as day\n" +
                        "from revlog %s\n" +
                        "group by day order by day)",
                mCol.getSched().getDayCutoff(), lim);
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            if (cur.moveToFirst()) {
                return new int[] {cur.getInt(0), cur.getInt(1)};
            } else {
                return new int[] {0, 0};
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    /**
     * Intervals
     * ***********************************************************
     */

    private String ivlGraph() {
        List<Object> i = _ivls();
        List<int[]> ivls = (List<int[]>) i.get(0);
        int all = (int) i.get(1);
        float avg = (float) i.get(2);
        int max = (int) i.get(3);

        if (ivls.size() == 0 || all == 0) {
            
        }

    }

    private List<Object> _ivls() {
        Integer chunk = 0;
        String lim;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            chunk = 1; lim = " and grp <= 30";
        } else if (mType == Stats.AxisType.TYPE_YEAR) {
            chunk = 7; lim = " and grp <= 52";
        } else (mType == Stats.AxisType.TYPE_LIFE) {
            chunk = 30; lim = "";
        }
        List<Object> data = new ArrayList<>();
        Cursor cur = null;
        Cursor cur2 = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(String.format(Locale.US,
                    "select ivl / %d as grp, count() from cards\n" +
                    "where did in %s and queue = 2 %s\n" +
                    "group by grp\n" +
                    "order by grp",
                    chunk, _limit(), lim), null);
            List<int[]> list = new ArrayList<>();
            while (cur.moveToNext()) {
                list.add(new int[]{cur.getInt(0), cur.getInt(1)});
            }
            cur2 = mCol.getDb().getDatabase().rawQuery(String.format(Locale.US,
                    "select count(), avg(ivl), max(ivl) from cards where did in %s and queue = 2",
                    _limit()), null);
            cur.moveToFirst();
            data.add(list);
            data.add(cur.getInt(0));
            data.add(cur.getFloat(1));
            data.add(cur.getInt(2));
            return data;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
            if (cur2 != null && !cur2.isClosed()) {
                cur2.close();
            }
        }
    }


    private String easeGraph() {
        // 3 + 4 + 4 + spaces on sides and middle = 15
        // yng starts at 1+3+1 = 5
        // mtr starts at 5+4+1 = 10
        Map<String, List<int[]>> d = new HashMap<>();
        d.put("lrn", new ArrayList<int[]>());
        d.put("yng", new ArrayList<int[]>());
        d.put("mtr", new ArrayList<int[]>());
        String[] types = new String[] {"lrn", "yng", "mtr"};
        List<int[]> eases = _eases();
        for (int[] i : eases) {
            int type = i[0];
            int ease = i[1];
            int cnt = i[2];
            if (type == 1) {
                ease += 5;
            } else if (type == 2) {
                ease += 10;
            }
            String n = types[type];
            d.get(n).add(new int[]{ease, cnt});
        }
        int[][] ticks = new int[][]{{1,1},{2,2},{3,3},
                                    {6,1},{7,2},{8,3},{9,4},
                                    {11, 1},{12,2},{13,3},{14,4}};
        String txt = _title("Answer Buttons", "The number of times you have pressed each button.");
        // txt = graph stuff TODO
        txt += _easeInfo(eases);
        return txt;
    }

    private String _easeInfo(List<int[]> eases) {
        Map<Integer, int[]> types = new HashMap<>();
        types.put(0, new int[]{0, 0});
        types.put(1, new int[]{0, 0});
        types.put(2, new int[]{0, 0});
        List<String> i = new ArrayList<>();
        for (int[] e : eases) {
            int type = e[0];
            int ease = e[1];
            int cnt = e[2];
            int bad = types.get(type)[0];
            int good = types.get(type)[1];
            int tot = bad + good;
            float pct = good / (float) tot * 100;
            if (pct == Float.NaN) {
                pct = 0;
            }
            i.add(String.format(Locale.getDefault(),
                    "Correct: <b>%0.2f%%</b><br>(d of %d)",
                    pct, good, tot));
            return TextUtils.join("<br>", i); // TODO: do the proper table thing
        }
        return "";
    }


    private List<int[]> _eases() {
        List<String> lims = new ArrayList<>();
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lims.add(lim);
        }
        Integer days;
        if (mType == Stats.AxisType.TYPE_MONTH) {
            days = 30;
        } else if (mType == Stats.AxisType.TYPE_YEAR) {
            days = 365;
        } else {
            days = null;
        }
        if (days != null) {
            lims.add(String.format(Locale.US, "id > %d",
                    (mCol.getSched().getDayCutoff()-(days*86400))*1000));
        }
        if (lims.size() > 0) {
            lim = "where " + TextUtils.join(" and ", lims);
        } else {
            lim = "";
        }
        String query = String.format(Locale.US,
                "select (case\n" +
                "when type in (0,2) then 0\n" +
                "when lastIvl < 21 then 1\n" +
                "else 2 end) as thetype,\n" +
                "(case when type in (0,2) and ease = 4 then 3 else ease end), count() from revlog %s\n" +
                "group by thetype, ease\n" +
                "order by thetype, ease", lim);
        Cursor cur = null;
        List<int[]> results = new ArrayList<>();
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                results.add(new int[]{cur.getInt(0), cur.getInt(1), cur.getInt(2)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return results;
    }


    /**
     * Hourly retention
     * ***********************************************************
     */

    private String hourGraph() {
        List<int[]> data = _hourRet();
        if (data.size() == 0) {
            return "";
        }
        List<int[]> shifted = new ArrayList<>();
        List<int[]> counts = new ArrayList<>();
        int mcount = 0;
        List<float[]> trend = new ArrayList<>();
        int peak = 0;
        for (int[] d : data) {
            int hour = (d[0] - 4) % 24;
            int pct = d[1];
            if (pct > peak) {
                peak = pct;
            }
            shifted.add(new int[]{hour, pct});
            counts.add(new int[]{hour, d[2]});
            if (d[2] > mcount) {
                mcount = d[2];
            }
        }
        // TODO: need to sort the lists with custom comparator
        // shifted.sort()
        // counts.sort()
        if (counts.size() < 4) {
            return "";
        }
        for (int[] d : shifted) {
            int hour = d[0];
            int pct = d[1];
            if (trend.size() == 0) {
                trend.add(new float[]{hour, pct});
            } else {
                float prev = trend.get(trend.size() - 1)[1];
                float diff = pct-prev;
                diff /= 3.0;
                diff = Math.round(diff); // TODO: rounding to n digits?
                trend.add(new float[]{hour, prev+diff});
            }
        }
        String txt = _title("Hourly Breakdown",
                            "Review success rate for each hour of the day.");
        // TODO: graphing txt +=
        txt += "Hours with less than 30 reviews are not shown.";
        return txt;
    }


    public List<int[]> _hourRet() {
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lim = " and " + lim;
        }
        // TODO: is this correct? do we need to x1000 ?
        Date sd = new Date(mCol.getCrt());

        Integer pd = _periodDays();
        if (pd != null) {
            lim += String.format(Locale.US, " and id > %d",
                    (mCol.getSched().getDayCutoff()-(86400*pd))*1000);
        }
        String query = String.format(Locale.US,
                "select\n" +
                "23 - ((cast((%d - id/1000) / 3600.0 as int)) %% 24) as hour,\n" +
                "sum(case when ease = 1 then 0 else 1 end) /\n" +
                "cast(count() as float) * 100,\n" + // TODO: is this a float? does it matter?
                "count()\n" +
                "from revlog where type in (0,1,2) %s\n" +
                "group by hour having count() > 30 order by hour",
                mCol.getSched().getDayCutoff()-(sd.getHours()*3600), lim); // TODO: deprecated?
        Cursor cur = null;
        List<int[]> results = new ArrayList<>();
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                results.add(new int[]{cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return results;
    }


    /**
     * Cards
     * ***********************************************************
     */

    private String cardGraph() {
        // graph data
        List<int[]> div = _cards();
        // TODO: skipped huge block for graphing data
        // text data
        List<String> i = new ArrayList<>();
        Cursor cur = null;
        String query = String.format(Locale.US,
                        "select count(id), count(distinct nid) from cards\n" +
                        "where did in %s ", _limit());
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            cur.moveToFirst();
            int c = cur.getInt(0);
            int f = cur.getInt(1);
            _line(i, "Total cards", c);
            _line(i, "Total notes", f);
            float[] f = _factors();
            float low = f[0];
            float avg = f[1];
            float high = f[2];
            if (low > 0) {
                _line(i, "Lowest ease", String.format(Locale.getDefault(), "%d%%", low));
                _line(i, "Average ease", String.format(Locale.getDefault(), "%d%%", avg));
                _line(i, "Highest ease", String.format(Locale.getDefault(), "%d%%", high));
            }
            String info = TextUtils.join("", i); // TODO: do we do table stuff?
            info += "A card's <i>ease</i> is the size of the next interval \n" +
                    "when you answer \"good\" on a review.";
            String txt = _title("Cards Types", "The division of cards in your deck(s)");
            // TODO: txt = graph stuff
            return txt + info;
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return "";
    }

    private void _line(List<String> i, String a, String b) {
        _line(i, a, b, true);
    }

    private void _line(List<String> i, String a, String b, boolean bold) {
        String colon = ":";
        if (bold) {
            i.add(String.format(Locale.getDefault(),
                    "<tr><td width=200 align=right>%s%s</td><td><b>%s</b></td></tr>", a, colon, b));
        } else {
            i.add(String.format(Locale.getDefault(),
                    "<tr><td width=200 align=right>%s%s</td><td>%s</td></tr>", a, colon, b));
        }
    }


    private String _lineTbl(List<String> i) {
        return "<table width=400>" + TextUtils.join("", i) + "</table>";
    }


    private float[] _factors() {
        String query = String.format(Locale.US,
                "select\n" +
                "min(factor) / 10.0,\n" +
                "avg(factor) / 10.0,\n" +
                "max(factor) / 10.0\n" +
                "from cards where did in %s and queue = 2", _limit());
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            cur.moveToFirst();
            return new float[] {cur.getFloat(0), cur.getFloat(1),cur.getFloat(2) };
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
    }


    private List<int[]> _cards() {
        String query = String.format(Locale.US,
                "select\n" +
                "sum(case when queue=2 and ivl >= 21 then 1 else 0 end), -- mtr\n" +
                "sum(case when queue in (1,3) or (queue=2 and ivl < 21) then 1 else 0 end), -- yng/lrn\n" +
                "sum(case when queue=0 then 1 else 0 end), -- new\n" +
                "sum(case when queue<0 then 1 else 0 end) -- susp\n" +
                "from cards where did in %s", _limit());
        Cursor cur = null;
        List<int[]> results = new ArrayList<>();
        try {
            cur = mCol.getDb().getDatabase().rawQuery(query, null);
            while (cur.moveToNext()) {
                results.add(new int[]{cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return results;
    }


    /**
     * Footer
     * ***********************************************************
     */

    private String footer() {
        String b = "<br><br><font size=1>";
        b += String.format(Locale.getDefault(), "Generated on %s",
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())); // TODO: get proper time
        b += "<br>";
        String deck;
        if (mWholeCollection) {
            deck = "Whole collection";
        } else {
            deck = mCol.getDecks().current().optString("name", "");
        }
        b += String.format(Locale.getDefault(), "Scope: %s", deck);
        b += "<br>";
        b += String.format(Locale.getDefault(), "Period: %s",
                new String[]{"1 month", "1 year", "deck life"}[mType.ordinal()]);
        return b;
    }

    /**
     * Tools
     * ***********************************************************
     */

    // TODO: we probably won't use this?
    private String _graph(){
        return "";
    }


    private String _limit() {
        if (mWholeCollection) {
            List<Long> ids = new ArrayList<>();
            try {
                for (JSONObject d : mCol.getDecks().all()) {
                    ids.add(d.getLong("id"));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return Utils.ids2str(Utils.arrayList2array(ids));
        }
        return mCol.getSched()._deckLimit();
    }


    private String _revlogLimit() {
        if (mWholeCollection) {
            return "";
        }
        return String.format(Locale.US, "cid in (select id from cards where did in %s)",
                Utils.ids2str(mCol.getDecks().active()));
    }


    private String _title(String title) {
        return _title(title, "");
    }


    private String _title(String title, String subtitle) {
        return String.format(Locale.getDefault(), "<h1>%s</h1>%s", title, subtitle);
    }


    private int _deckAge(String by) {
        String lim = _revlogLimit();
        if (!TextUtils.isEmpty(lim)) {
            lim = " where " + lim;
        }
        int t = 0;
        if (by.equals("review")) {
            t = mCol.getDb().queryScalar(String.format(Locale.US,
                    "select id from revlog %s order by id limit 1", lim));
        } else if (by.equals("add")) {
            lim = String.format(Locale.US, "where did in %s", Utils.ids2str(mCol.getDecks().active()));
            t = mCol.getDb().queryScalar(String.format(Locale.US,
                    "select id from cards %s order by id limit 1", lim));
        }
        int period;
        if (t > 0) {
            period = 1;
        } else {
            period = Math.max(1, (int) (1+((mCol.getSched().getDayCutoff() - (t/1000)) / 86400)));
        }
        return period;
    }


    private Integer _periodDays() {
        switch (mType) {
            case TYPE_MONTH:
                return 30;
            case TYPE_YEAR:
                return 365;
            default:
            case TYPE_LIFE:
                return null;
        }
    }


    private String _avgDay(double tot, int num, String unit) {
        List<String> vals = new ArrayList<>();
        vals.add(String.format(Locale.US, "%0.1f %s/day", tot / (float) num, unit));
        return TextUtils.join(", ", vals);
    }


    private String bold(Object s) {
        return "<b>"+s+"<b>";
    }
}
