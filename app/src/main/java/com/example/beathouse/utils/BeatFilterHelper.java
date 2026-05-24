package com.example.beathouse.utils;

import com.example.beathouse.models.Beat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeatFilterHelper {

    public static List<Beat> applyFilters(List<Beat> allBeats, String query, String selectedGenre,
                                          String searchTag, int minBpm, int maxBpm, String sortType,
                                          boolean includeProducerInSearch) {
        List<Beat> result = new ArrayList<>();

        // 1. Filter by genre
        if (selectedGenre == null || "All".equalsIgnoreCase(selectedGenre)) {
            result.addAll(allBeats);
        } else {
            for (Beat b : allBeats) {
                if (selectedGenre.equalsIgnoreCase(b.getGenre())) {
                    result.add(b);
                }
            }
        }

        // 2. Filter by search query
        if (query != null && !query.isEmpty()) {
            List<Beat> searchFiltered = new ArrayList<>();
            String lowerQuery = query.toLowerCase();
            for (Beat beat : result) {
                boolean matches = beat.getTitle().toLowerCase().contains(lowerQuery);
                if (!matches && includeProducerInSearch && beat.getUserName() != null) {
                    matches = beat.getUserName().toLowerCase().contains(lowerQuery);
                }
                if (matches) {
                    searchFiltered.add(beat);
                }
            }
            result = searchFiltered;
        }

        // 3. Filter by tags
        if (searchTag != null && !searchTag.isEmpty()) {
            List<Beat> tagFiltered = new ArrayList<>();
            String lowerTag = searchTag.toLowerCase();
            for (Beat beat : result) {
                if (containsTag(beat, lowerTag)) {
                    tagFiltered.add(beat);
                }
            }
            result = tagFiltered;
        }

        // 4. Filter by BPM range
        if (minBpm > 0 || maxBpm > 0) {
            List<Beat> bpmFiltered = new ArrayList<>();
            for (Beat beat : result) {
                int bpm = beat.getBpm();
                boolean bpmOk = true;
                if (minBpm > 0 && bpm < minBpm) bpmOk = false;
                if (maxBpm > 0 && bpm > maxBpm) bpmOk = false;
                if (bpmOk) bpmFiltered.add(beat);
            }
            result = bpmFiltered;
        }

        // 5. Sorting
        sortBeats(result, sortType);

        return result;
    }

    private static boolean containsTag(Beat beat, String tag) {
        if (beat == null || tag == null) return false;

        String description = beat.getDescription();
        if (description == null || description.isEmpty()) return false;

        Pattern pattern = Pattern.compile("#(\\w+)");
        Matcher matcher = pattern.matcher(description.toLowerCase());

        while (matcher.find()) {
            String foundTag = matcher.group(1);
            if (foundTag.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void sortBeats(List<Beat> beats, String sortType) {
        if (sortType == null) return;

        switch (sortType) {
            case "price_asc":
                Collections.sort(beats, (a, b) -> Double.compare(a.getPriceMp3Wav(), b.getPriceMp3Wav()));
                break;
            case "price_desc":
                Collections.sort(beats, (a, b) -> Double.compare(b.getPriceMp3Wav(), a.getPriceMp3Wav()));
                break;
            case "bpm_asc":
                Collections.sort(beats, (a, b) -> Integer.compare(a.getBpm(), b.getBpm()));
                break;
            case "bpm_desc":
                Collections.sort(beats, (a, b) -> Integer.compare(b.getBpm(), a.getBpm()));
                break;
            default:
                break;
        }
    }
}
