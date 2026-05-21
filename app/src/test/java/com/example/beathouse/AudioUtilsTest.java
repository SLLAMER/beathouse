package com.example.beathouse;

import com.example.beathouse.utils.AudioUtils;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class AudioUtilsTest {

    @Test
    public void testSplitAudioIntoChunks() {
        String testData = "1234567890abcdefghij"; // 20 chars
        List<String> chunks = AudioUtils.splitAudioIntoChunks(testData);

        assertNotNull(chunks);
        if (testData.length() < AudioUtils.MAX_CHUNK_SIZE) {
            assertEquals(1, chunks.size());
            assertEquals(testData, chunks.get(0));
        }
    }

    @Test
    public void testCombineChunks() {
        List<String> chunks = Arrays.asList("chunk1", "chunk2", "chunk3");
        String combined = AudioUtils.combineChunks(chunks);
        assertEquals("chunk1chunk2chunk3", combined);
    }

    @Test
    public void testEstimatedSize() {
        String base64 = "YmVhdGhvdXNl"; // "beathouse" in base64, length 12
        long size = AudioUtils.getEstimatedFileSize(base64);
        // 12 * 0.75 = 9
        assertEquals(9, size);
    }
}
