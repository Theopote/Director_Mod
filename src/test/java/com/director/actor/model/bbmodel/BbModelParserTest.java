package com.director.actor.model.bbmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbModelParserTest {
    private static final String MINIMAL = """
            {
              "meta": { "format_version": "4.5", "model_format": "free" },
              "resolution": { "width": 64, "height": 64 },
              "elements": [
                {
                  "uuid": "e1",
                  "name": "torso",
                  "from": [-3, 0, -2],
                  "to": [3, 7, 2],
                  "origin": [0, 0, 0]
                }
              ],
              "outliner": [
                {
                  "name": "root",
                  "uuid": "b0",
                  "origin": [0, 0, 0],
                  "children": [
                    {
                      "name": "torso",
                      "uuid": "b1",
                      "origin": [0, 12, 0],
                      "children": ["e1"]
                    }
                  ]
                }
              ]
            }
            """;

    @Test
    void parsesMinimalModel() throws Exception {
        BbModelFile file = BbModelParser.parse(MINIMAL);

        assertEquals("4.5", file.meta.format_version);
        assertEquals(1, file.elements.size());
        assertEquals("e1", file.elements.getFirst().uuid);
        assertEquals(1, file.outliner.size());
        assertEquals("root", file.outliner.getFirst().name);
        assertEquals(1, file.outliner.getFirst().children.size());
        assertTrue(file.outliner.getFirst().children.getFirst() instanceof BbModelFile.OutlinerGroupChild);
    }

    @Test
    void rejectsMissingOutliner() {
        BbModelImportException error = assertThrows(
                BbModelImportException.class,
                () -> BbModelParser.parse("""
                        { "elements": [] }
                        """));
        assertTrue(error.getMessage().contains("outliner"));
    }

    @Test
    void rejectsInvalidJson() {
        BbModelImportException error = assertThrows(
                BbModelImportException.class,
                () -> BbModelParser.parse("{ not json"));
        assertTrue(error.getMessage().contains("bbmodel 文件校验失败"));
    }
}
