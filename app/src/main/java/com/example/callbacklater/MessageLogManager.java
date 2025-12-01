package com.example.callbacklater;

import android.content.Context;
import android.util.Xml;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Helper to persist message-sent records as an XML file in internal storage.
 *
 * File: /data/data/<package>/files/message_log.xml
 * Format:
 * <messages>
 *   <message>
 *     <contact>+1234567890</contact>
 *     <time>2025-11-24T17:00:00Z</time>
 *   </message>
 * </messages>
 */
public class MessageLogManager {
    private static final String TAG = "MessageLogManager";
    private static final String FILENAME = "message_log.xml";

    public static class Entry {
        public final String contact;
        public final String timeIso; // ISO 8601 string in UTC

        public Entry(String contact, String timeIso) {
            this.contact = contact;
            this.timeIso = timeIso;
        }
    }

    /**
     * Add a log entry for a message sent to `contact` at `timestampMillis`.
     */
    public static synchronized void addEntry(Context ctx, String contact, long timestampMillis) {
        if (contact == null) contact = "";

        List<Entry> entries = readAll(ctx);
        String iso = toIsoUtc(timestampMillis);
        entries.add(new Entry(contact, iso));

        writeAll(ctx, entries);
    }

    /**
     * Read all entries from the XML file. Returns empty list if none or on error.
     */
    public static synchronized List<Entry> readAll(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILENAME);
        if (!f.exists()) return new ArrayList<>();

        InputStream in = null;
        List<Entry> out = new ArrayList<>();
        try {
            in = new FileInputStream(f);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "utf-8");

            int event = parser.getEventType();
            String curTag = null;
            String contact = null;
            String time = null;

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    curTag = parser.getName();
                    if ("message".equals(curTag)) {
                        contact = null; time = null;
                    }
                } else if (event == XmlPullParser.TEXT) {
                    String text = parser.getText();
                    if ("contact".equals(curTag)) contact = text;
                    else if ("time".equals(curTag)) time = text;
                } else if (event == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("message".equals(name)) {
                        if (contact == null) contact = "";
                        if (time == null) time = "";
                        out.add(new Entry(contact, time));
                    }
                    curTag = null;
                }
                event = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            Log.w(TAG, "Failed to read message log", e);
            return new ArrayList<>();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }

        return out;
    }

    private static synchronized void writeAll(Context ctx, List<Entry> entries) {
        File f = new File(ctx.getFilesDir(), FILENAME);
        OutputStream out = null;
        try {
            out = new FileOutputStream(f, false);
            XmlSerializer s = Xml.newSerializer();
            s.setOutput(out, "utf-8");
            s.startDocument("utf-8", true);
            s.startTag(null, "messages");

            for (Entry e : entries) {
                s.startTag(null, "message");

                s.startTag(null, "contact");
                s.text(e.contact == null ? "" : e.contact);
                s.endTag(null, "contact");

                s.startTag(null, "time");
                s.text(e.timeIso == null ? "" : e.timeIso);
                s.endTag(null, "time");

                s.endTag(null, "message");
            }

            s.endTag(null, "messages");
            s.endDocument();
            s.flush();
        } catch (IOException ex) {
            Log.w(TAG, "Failed to write message log", ex);
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    private static String toIsoUtc(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millis));
    }

    /**
     * Filter entries by optional contact substring (case insensitive) and optional date range.
     * Use null to skip each filter.
     */
    public static synchronized List<Entry> filterEntries(Context ctx, String contactSubstr, Long startTimeMillis, Long endTimeMillis) {
        List<Entry> all = readAll(ctx);
        List<Entry> filtered = new ArrayList<>();
        for (Entry e : all) {
            boolean matches = true;
            if (contactSubstr != null && !contactSubstr.isEmpty()) {
                if (e.contact == null || !e.contact.toLowerCase().contains(contactSubstr.toLowerCase())) {
                    matches = false;
                }
            }
            if (startTimeMillis != null) {
                long entryTime = parseIsoToMillis(e.timeIso);
                if (entryTime < startTimeMillis) matches = false;
            }
            if (endTimeMillis != null) {
                long entryTime = parseIsoToMillis(e.timeIso);
                if (entryTime > endTimeMillis) matches = false;
            }
            if (matches) filtered.add(e);
        }
        return filtered;
    }

    /**
     * Delete an entry from the log matching both contact and timeIso.
     * Returns true if an entry was deleted.
     */
    public static synchronized boolean deleteEntry(Context ctx, Entry toDelete) {
        List<Entry> entries = readAll(ctx);
        boolean removed = entries.removeIf(e -> e.contact.equals(toDelete.contact) && e.timeIso.equals(toDelete.timeIso));
        if (removed) {
            writeAll(ctx, entries);
        }
        return removed;
    }

    public static long parseIsoToMillis(String iso) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(iso);
            if (d != null) return d.getTime();
            else return 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }
}
