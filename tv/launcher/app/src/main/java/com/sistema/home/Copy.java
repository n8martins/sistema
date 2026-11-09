package com.sistema.home;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Everything the screen says to you.
 *
 * Quotes are shuffled like a deck and dealt one at a time, so nothing repeats until you
 * have seen them all. A new one is dealt every time the TV wakes up.
 */
final class Copy {
    private Copy() {}

    /** {quote, author}. Attributions checked; misattributed classics were left out on purpose. */
    static final String[][] QUOTES = {
            {"The impediment to action advances action. What stands in the way becomes the way.", "Marcus Aurelius"},
            {"Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"},
            {"If it is not right, do not do it. If it is not true, do not say it.", "Marcus Aurelius"},
            {"We suffer more often in imagination than in reality.", "Seneca"},
            {"It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"},
            {"First say to yourself what you would be, and then do what you have to do.", "Epictetus"},
            {"How long are you going to wait before you demand the best for yourself?", "Epictetus"},
            {"He who has a why to live can bear almost any how.", "Friedrich Nietzsche"},
            {"The credit belongs to the man who is actually in the arena, whose face is marred by dust and sweat and blood.", "Theodore Roosevelt"},
            {"I hated every minute of training, but I said, ‘Don't quit. Suffer now and live the rest of your life as a champion.’", "Muhammad Ali"},
            {"Champions aren't made in gyms. Champions are made from something they have deep inside them: a desire, a dream, a vision.", "Muhammad Ali"},
            {"I've failed over and over and over again in my life. And that is why I succeed.", "Michael Jordan"},
            {"Everything negative, pressure, challenges, is all an opportunity for me to rise.", "Kobe Bryant"},
            {"Rest at the end, not in the middle.", "Kobe Bryant"},
            {"If you no longer go for a gap that exists, you are no longer a racing driver.", "Ayrton Senna"},
            {"Talent without working hard is nothing.", "Cristiano Ronaldo"},
            {"Discipline is doing what you hate to do, but doing it like you love it.", "Mike Tyson"},
            {"Everybody has a plan until they get punched in the mouth.", "Mike Tyson"},
            {"There's no talent here, this is hard work. This is an obsession.", "Conor McGregor"},
            {"Don't stop when you're tired. Stop when you're done.", "David Goggins"},
            {"You are in danger of living a life so comfortable and soft that you will die without ever realizing your true potential.", "David Goggins"},
            {"Discipline equals freedom.", "Jocko Willink"},
            {"Don't count on motivation. Count on discipline.", "Jocko Willink"},
            {"The mind is the limit. As long as the mind can envision the fact that you can do something, you can do it.", "Arnold Schwarzenegger"},
            {"Absorb what is useful, discard what is useless, and add what is specifically your own.", "Bruce Lee"},
            {"Do not pray for an easy life. Pray for the strength to endure a difficult one.", "Bruce Lee"},
            {"Your time is limited, so don't waste it living someone else's life.", "Steve Jobs"},
            {"When something is important enough, you do it even if the odds are not in your favor.", "Elon Musk"},
            {"Escape competition through authenticity.", "Naval Ravikant"},
            {"Learn to sell. Learn to build. If you can do both, you will be unstoppable.", "Naval Ravikant"},
            {"Play long term games with long term people.", "Naval Ravikant"},
            {"Volume negates luck.", "Alex Hormozi"},
            {"You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear"},
            {"Every action you take is a vote for the type of person you wish to become.", "James Clear"},
            {"Discipline is the bridge between goals and accomplishment.", "Jim Rohn"},
            {"Don't wish it were easier. Wish you were better.", "Jim Rohn"},
            {"Ease is a greater threat to progress than hardship.", "Denzel Washington"},
            {"Without commitment, you'll never start. But more importantly, without consistency, you'll never finish.", "Denzel Washington"},
            {"Success isn't always about greatness. It's about consistency.", "Dwayne Johnson"},
            {"Be so good they can't ignore you.", "Steve Martin"},
            {"Inspiration is for amateurs. The rest of us just show up and get to work.", "Chuck Close"},
            {"Compare yourself to who you were yesterday, not to who someone else is today.", "Jordan Peterson"},
            {"Hard choices, easy life. Easy choices, hard life.", "Jerzy Gregorek"},
            {"Pressure is a privilege.", "Billie Jean King"},
            {"We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant"},
    };

    private static final int EARLY = 0, MORNING = 1, AFTERNOON = 2, EVENING = 3, NIGHT = 4;

    private static final String[][] LINES = {
            { // 4:00 to 6:59
                    "Up before the noise. That's the edge.",
                    "The city is still asleep. You're already moving.",
                    "Quiet hours build loud results.",
            },
            { // 7:00 to 11:59
                    "Win the morning. The day follows.",
                    "Set the tone before the world sets it for you.",
                    "Hard thing first. Everything after gets easier.",
                    "Train first. Then go build.",
            },
            { // 12:00 to 16:59
                    "Stay locked in. The second half decides it.",
                    "Momentum is a choice. Keep making it.",
                    "Do the thing you've been avoiding. Now.",
            },
            { // 17:00 to 20:59
                    "Close the day out strong.",
                    "Recovery is part of the work. Treat it that way.",
                    "Keep what worked today. Cut what didn't.",
            },
            { // 21:00 to 3:59
                    "Shut it down. Tomorrow's standard starts with sleep.",
                    "Rest like it's part of the plan. It is.",
                    "Lights low. Phone down. Reset.",
            },
    };

    private static int slot(Calendar c) {
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= 4 && h < 7) return EARLY;
        if (h >= 7 && h < 12) return MORNING;
        if (h >= 12 && h < 17) return AFTERNOON;
        if (h >= 17 && h < 21) return EVENING;
        return NIGHT;
    }

    /** One line under the greeting, tuned to the day and the hour. */
    static String line(Calendar c, long seed) {
        int s = slot(c);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        Random r = new Random(seed * 31 + c.get(Calendar.DAY_OF_YEAR) * 7 + s);
        String special = null;
        if (dow == Calendar.MONDAY && (s == EARLY || s == MORNING)) special = "New week. Same standard.";
        else if (dow == Calendar.FRIDAY && (s == EVENING || s == NIGHT)) special = "Strong week. Don't let the weekend undo it.";
        else if (dow == Calendar.SATURDAY && (s == EARLY || s == MORNING)) special = "No schedule today. Keep the standard anyway.";
        else if (dow == Calendar.SUNDAY && s != NIGHT) special = "Sunday reset. Plan the week before it plans you.";
        else if (dow == Calendar.SUNDAY) special = "Tomorrow starts tonight. Lay it out, then sleep.";
        if (special != null && r.nextBoolean()) return special;
        String[] pool = LINES[s];
        return pool[r.nextInt(pool.length)];
    }

    /** "DAY 313 OF 365" plus progress through the year (0..1) and days left. */
    static int dayOfYear(Calendar c) {
        return c.get(Calendar.DAY_OF_YEAR);
    }

    static int daysInYear(Calendar c) {
        return c.getActualMaximum(Calendar.DAY_OF_YEAR);
    }

    /** Your own quotes, added from the phone. Stored as a JSON array of [text, author]. */
    static List<String[]> custom(SharedPreferences p) {
        List<String[]> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString(Prefs.CUSTOM_QUOTES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONArray q = a.getJSONArray(i);
                out.add(new String[]{q.getString(0), q.optString(1, "")});
            }
        } catch (org.json.JSONException ignored) {
        }
        return out;
    }

    /** Built in quotes first, then yours. Deck indexes point into this list. */
    static List<String[]> all(SharedPreferences p) {
        List<String[]> out = new ArrayList<>();
        Collections.addAll(out, QUOTES);
        out.addAll(custom(p));
        return out;
    }

    static String[] quote(SharedPreferences p, int index) {
        List<String[]> all = all(p);
        if (index < 0 || index >= all.size()) return QUOTES[0];
        return all.get(index);
    }

    /** Adds one of your quotes and puts it on top of the deck, so it shows next. */
    static void addCustom(SharedPreferences p, String text, String author) {
        List<String[]> mine = custom(p);
        mine.add(new String[]{text, author == null ? "" : author});
        saveCustom(p, mine);
        int newIndex = QUOTES.length + mine.size() - 1;
        List<Integer> order = shuffled(new Random(), QUOTES.length + mine.size());
        order.remove(Integer.valueOf(newIndex));
        order.add(0, newIndex);
        p.edit().putString(Prefs.Q_ORDER, join(order)).putInt(Prefs.Q_POS, -1).apply();
    }

    static void removeCustom(SharedPreferences p, int index) {
        List<String[]> mine = custom(p);
        if (index < 0 || index >= mine.size()) return;
        mine.remove(index);
        saveCustom(p, mine);
        List<Integer> order = shuffled(new Random(), QUOTES.length + mine.size());
        p.edit().putString(Prefs.Q_ORDER, join(order)).putInt(Prefs.Q_POS, 0)
                .putLong(Prefs.Q_TIME, System.currentTimeMillis()).apply();
    }

    private static void saveCustom(SharedPreferences p, List<String[]> mine) {
        org.json.JSONArray a = new org.json.JSONArray();
        for (String[] q : mine) {
            org.json.JSONArray item = new org.json.JSONArray();
            item.put(q[0]);
            item.put(q[1]);
            a.put(item);
        }
        p.edit().putString(Prefs.CUSTOM_QUOTES, a.toString()).apply();
    }

    /** Current quote index from the shuffled deck. */
    static int currentQuote(SharedPreferences p) {
        List<Integer> order = order(p);
        int pos = p.getInt(Prefs.Q_POS, 0);
        if (pos < 0 || pos >= order.size()) pos = 0;
        return order.get(pos);
    }

    /** Deals the next quote and returns its index. */
    static int nextQuote(SharedPreferences p) {
        List<Integer> order = order(p);
        int pos = p.getInt(Prefs.Q_POS, -1) + 1;
        SharedPreferences.Editor e = p.edit();
        if (pos >= order.size()) {
            int last = order.get(order.size() - 1);
            order = shuffled(new Random(), order.size());
            // Never show the same quote twice in a row across a reshuffle.
            if (order.get(0) == last) Collections.swap(order, 0, order.size() - 1);
            e.putString(Prefs.Q_ORDER, join(order));
            pos = 0;
        }
        e.putInt(Prefs.Q_POS, pos).putLong(Prefs.Q_TIME, System.currentTimeMillis()).apply();
        return order.get(pos);
    }

    private static List<Integer> order(SharedPreferences p) {
        int size = QUOTES.length + custom(p).size();
        String s = p.getString(Prefs.Q_ORDER, null);
        List<Integer> out = new ArrayList<>();
        if (s != null) {
            for (String part : s.split(",")) {
                try {
                    int i = Integer.parseInt(part);
                    if (i >= 0 && i < size && !out.contains(i)) out.add(i);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (out.size() != size) {
            out = shuffled(new Random(), size);
            p.edit().putString(Prefs.Q_ORDER, join(out)).putInt(Prefs.Q_POS, -1).apply();
        }
        return out;
    }

    private static List<Integer> shuffled(Random r, int size) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < size; i++) l.add(i);
        Collections.shuffle(l, r);
        return l;
    }

    private static String join(List<Integer> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(',');
            b.append(l.get(i));
        }
        return b.toString();
    }
}
