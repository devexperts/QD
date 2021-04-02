/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.tools;

import com.devexperts.io.CSVReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds MIC database from original ISO 10383 files.
 * Source files shall be converted to CSV format and placed into separate folder.
 * This builder will read all files and compile MIC database. The output can then
 * be copy-pasted into MIC class.
 * <p>
 * This is an excerpt from instruction to (re)build local MIC database:
 * <ul>
 * <li>Download missing source files from ISO 10383 home. The files are named/located similarly to this one: <a href="http://www.iso15022.org/MIC/ISO10383_MIC_v1_48.xls">http://www.iso15022.org/MIC/ISO10383_MIC_v1_48.xls</a>
 * <li>For each version create following CSV-files and place them into single folder:
 * <br>ISO10383_MIC_v1_48.csv - for the main list that contains all MICs with their websites (typically list of MICs ordered by country)
 * <br>ISO10383_MIC_v1_48.add.csv - for the list that contains all newly added MICs (can be omitted if list is empty)
 * <br>ISO10383_MIC_v1_48.mod.csv - for the list that contains all modified MICs (can be omitted if list is empty)
 * <br>ISO10383_MIC_v1_48.del.csv - for the list that contains all newly deleted MICs (can be omitted if list is empty)
 * <br>ISO10383_MIC_v1_48.hd.csv - for the list that contains historical data about deleted MICs (can be omitted if list is empty)
 * <li>Run MICBuilder tool to create MIC database from CSV-files and copy-paste it to MIC class.
 * </ul>
 */
public class MICBuilder {
    public static void main(String[] args) throws IOException {
        if (args.length != 1)
            printUsage();
        String[] files = listFiles(args[0]);
        if (files == null || files.length == 0)
            printUsage();
        // Read all files in proper order and incrementally update MIC list.
        Map<String, Data> mics = new HashMap<String, Data>();
        for (String file : files) {
            int fileType = getFileType(file);
            if (fileType <= 0 || getFileVersion(file) <= 0) {
                System.out.println("Skipping unrecognized file " + file);
                continue;
            }
            boolean resetPresence = true;
            Header header = null;
            for (String[] record : readCSV(new File(args[0], file))) {
                if (header == null) {
                    header = parseHeader(record);
                    continue;
                }
                String mic = get(record, header.mic);
                if (mic.length() != 4) {
                    System.out.println("Incorrect MIC \"" + mic + "\" in file " + file);
                    continue;
                }
                if (fileType == COMPLETE_FILE_TYPE && resetPresence) {
                    for (Data data : mics.values())
                        data.present = false;
                    resetPresence = false;
                }
                Data data = mics.get(mic);
                if (data == null)
                    mics.put(mic, data = new Data(mic));
                updateData(record, header, data);
                if (fileType == COMPLETE_FILE_TYPE)
                    data.present = true;
            }
            if (header == null)
                System.out.println("No header in file " + file);
        }
        // Write all MICs in a way suitable for copy-paste into MIC class.
        writeResult(mics);
    }

    private static void printUsage() {
        System.out.println("Creates MIC data file based on ISO 10383 standard files in CSV format.");
        System.out.println("Usage:  MICBuilder <ISO-folder>");
        System.exit(0);
    }

    private static String[] listFiles(String folder) {
        String[] files = new File(folder).list();
        if (files != null && files.length > 1)
            Arrays.sort(files, new Comparator<String>() {
                public int compare(String s1, String s2) {
                    int v1 = getFileVersion(s1);
                    int v2 = getFileVersion(s2);
                    if (v1 > 0 && v2 > 0 && v1 != v2)
                        return v1 - v2;
                    if (v1 < 0 || v2 < 0)
                        return s1.compareTo(s2);
                    int t1 = getFileType(s1);
                    int t2 = getFileType(s2);
                    if (t1 > 0 && t2 > 0 && t1 != t2)
                        return t1 - t2;
                    return s1.compareTo(s2);
                }
            });
        return files;
    }

    private static final String ISO_PREFIX = "ISO10383_MIC_v1_";
    private static final int COMPLETE_FILE_TYPE = 1;

    private static int getFileVersion(String s) {
        int i = s.indexOf('.');
        if (!s.regionMatches(true, 0, ISO_PREFIX, 0, ISO_PREFIX.length()) || i < 0)
            return -1;
        try {
            return Integer.parseInt(s.substring(ISO_PREFIX.length(), i));
        } catch (Exception e) {
            return -1;
        }
    }

    private static int getFileType(String s) {
        int i = s.indexOf('.');
        if (i < 0)
            return -1;
        s = s.substring(i);
        if (s.equalsIgnoreCase(".csv"))
            return COMPLETE_FILE_TYPE;
        if (s.equalsIgnoreCase(".add.csv"))
            return 2;
        if (s.equalsIgnoreCase(".mod.csv"))
            return 3;
        if (s.equalsIgnoreCase(".del.csv"))
            return 4;
        if (s.equalsIgnoreCase(".hd.csv"))
            return 5;
        return -1;
    }

    private static List<String[]> readCSV(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        if (in.read(buf) != buf.length)
            throw new IOException("Unexpected end of file " + f.getName());
        in.close();
        // Automatically determine separator (comma ',' or semicolon ';') by frequency of appearance.
        int commas = 0;
        int semicolons = 0;
        for (byte b : buf)
            if (b == ',')
                commas++;
            else if (b == ';')
                semicolons++;
        if (commas + semicolons < 5 || commas < semicolons * 2 && semicolons < commas * 2)
            throw new IOException("Cannot determine separator for file " + f.getName());
        char separator = commas > semicolons ? ',' : ';';
        // Read all records, skip empty records, and trim all values.
        List<String[]> records = new ArrayList<String[]>();
        for (String[] record : new CSVReader(new InputStreamReader(new ByteArrayInputStream(buf)), separator, '"').readAll()) {
            boolean empty = true;
            for (int i = 0; i < record.length; i++) {
                record[i] = trim(record[i]);
                if (record[i].length() > 0)
                    empty = false;
            }
            if (!empty)
                records.add(record);
        }
        return records;
    }

    private static String trim(String s) {
        s = s.trim();
        if (s.indexOf('\r') < 0 && s.indexOf('\n') < 0)
            return s;
        StringBuilder sb = new StringBuilder();
        for (String si : s.split("\r|\n"))
            if (si.trim().length() > 0)
                sb.append(sb.length() == 0 ? "" : " ").append(si.trim());
        return sb.toString();
    }

    private static class Header {
        int cc = -1;          // CC
        int country = -1;     // COUNTRY
        int city = -1;        // CITY
        int mic = -1;         // MIC
        int institution = -1; // "INSTITUTION DESCRIPTION"
        int acronym = -1;     // "ACCR" or "ACCR."
        int website = -1;     // "WEB SITE" or "WEBSITE"
        int comments = -1;    // COMMENTS
        int date = -1;        // "Date added" or "DELETION DATE" or "Date added/modified" or "DATE"
        int status = -1;      // STATUS

        Header() {}
    }

    private static Header parseHeader(String[] record) {
        Header header = new Header();
        String unrecognized = null;
        for (int i = 0; i < record.length; i++)
            if (record[i].equals("CC"))
                header.cc = i;
            else if (record[i].equals("COUNTRY"))
                header.country = i;
            else if (record[i].equals("CITY"))
                header.city = i;
            else if (record[i].equals("MIC"))
                header.mic = i;
            else if (record[i].equals("INSTITUTION DESCRIPTION"))
                header.institution = i;
            else if (record[i].startsWith("ACCR"))
                header.acronym = i;
            else if (record[i].startsWith("WEB") && record[i].endsWith("SITE"))
                header.website = i;
            else if (record[i].startsWith("COMMENT"))
                header.comments = i;
            else if (record[i].toUpperCase().indexOf("DATE") >= 0)
                header.date = i;
            else if (record[i].equals("STATUS"))
                header.status = i;
            else if (record[i].length() > 0)
                unrecognized = record[i];
        if (header.mic >= 0 && (header.cc >= 0 || header.country >= 0 || header.city >= 0)) {
            if (unrecognized != null)
                throw new IllegalArgumentException("Cannot recognize header " + unrecognized);
            return header;
        }
        return null;
    }

    private static class Data implements Comparable<Data> {
        String cc = "";
        String country = "";
        String city = "";
        final String mic;
        String institution = "";
        String acronym = "";
        String website = "";
        Set<String> comments = new LinkedHashSet<String>();
        String date = "";
        String status = "";
        boolean present;

        Data(String mic) {
            this.mic = mic;
        }

        public int compareTo(Data data) {
            if (!country.equals(data.country))
                return country.compareTo(data.country);
            if (!city.equals(data.city))
                return city.compareTo(data.city);
            return mic.compareTo(data.mic);
        }
    }

    private static void updateData(String[] record, Header header, Data data) {
        data.cc = merge(data.cc, get(record, header.cc));
        data.country = merge(data.country, get(record, header.country));
        data.city = merge(data.city, get(record, header.city));
        data.institution = merge(data.institution, get(record, header.institution));
        data.acronym = merge(data.acronym, get(record, header.acronym));
        data.website = merge(data.website, get(record, header.website));
        String comments = removeWebsite(get(record, header.comments), data.website);
        if (comments.length() > 0)
            data.comments.add(comments);
        data.date = merge(data.date, get(record, header.date));
        data.status = merge(data.status, get(record, header.status));
    }

    private static String get(String[] record, int i) {
        return i >= 0 && i < record.length ? record[i] : "";
    }

    private static String merge(String s1, String s2) {
        return s2.length() > 0 ? s2 : s1;
    }

    private static String capitalize(String s) {
        char[] cc = s.toCharArray();
        for (int i = 0; i < cc.length; i++)
            if (i == 0 || !Character.isLetterOrDigit(cc[i - 1]))
                cc[i] = Character.toUpperCase(cc[i]);
            else
                cc[i] = Character.toLowerCase(cc[i]);
        decapitalize(cc, "And"); // Trinidad And Tobago
        decapitalize(cc, "Of"); // Port Of Spain
        decapitalize(cc, "Es"); // Dar Es Salaam
        decapitalize(cc, "De"); // Rio De Janeiro
        return new String(cc);
    }

    private static void decapitalize(char[] cc, String sub) {
        for (int i = 0; i <= cc.length - sub.length(); i++) {
            boolean match = (i == 0 || !Character.isLetterOrDigit(cc[i - 1])) &&
                (i == cc.length - sub.length() || !Character.isLetterOrDigit(cc[i + sub.length()]));
            for (int j = 0; j < sub.length() && match; j++)
                if (cc[i + j] != sub.charAt(j))
                    match = false;
            if (match)
                for (int j = 0; j < sub.length(); j++)
                    cc[i + j] = Character.toLowerCase(cc[i + j]);
        }
    }

    private static String removeWebsite(String comment, String website) {
        if (comment.length() < website.length() || website.isEmpty())
            return comment;
        int begin = comment.indexOf(website);
        if (begin < 0)
            return comment;
        int end = begin + website.length();
        if (begin > 0 && comment.charAt(begin - 1) == '/' || end < comment.length() && comment.charAt(end) == '/')
            return comment;
        begin = removePrefix(comment, "website ", begin);
        begin = removePrefix(comment, "see ", begin);
        begin = removePrefix(comment, "(", begin);
        end = removeSuffix(comment, ".", end);
        end = removeSuffix(comment, ")", end);
        return (comment.substring(0, begin).trim() + " " + comment.substring(end).trim()).trim();
    }

    private static int removePrefix(String comment, String prefix, int i) {
        return comment.regionMatches(true, i - prefix.length(), prefix, 0, prefix.length()) ? i - prefix.length() : i;
    }

    private static int removeSuffix(String comment, String suffix, int i) {
        return comment.regionMatches(true, i, suffix, 0, suffix.length()) ? i + suffix.length() : i;
    }

    private static void writeResult(Map<String, Data> mics) {
        for (Data data : new TreeSet<Data>(mics.values()))
            System.out.println("add(\"" + quote(data.cc) + "\", \"" +
                quote(capitalize(data.country)) + "\", \"" + quote(capitalize(data.city)) + "\", \"" +
                quote(data.mic) + "\", \"" + quote(data.institution) + "\", \"" + quote(data.acronym) + "\", \"" +
                quote(data.website) + "\", \"" + quote(packComments(data.comments)) + "\", \"" +
                quote(data.date) + "\", \"" + quote(data.status) + "\", " + data.present + ");");
    }

    private static String quote(String s) {
        return s.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\\"", "\\\\\\\"");
    }

    private static String packComments(Collection<String> comments) {
        if (comments.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (String s : comments)
            if (s.length() > 0)
                sb.append(sb.length() == 0 ? "" : " ").append(s);
        return sb.toString();
    }
}
