package com.director.core.animation;

import java.util.*;

/** Sparse masks avoid allocating a volume between distant islands of blocks. */
final class GreedySurfaceMesher {
    record Cell(int u, int v) {}
    record Rectangle<T>(int u, int v, int width, int height, T material) {}

    static <T> List<Rectangle<T>> merge(Map<Cell, T> input) {
        var remaining = new TreeMap<Cell, T>(Comparator.comparingInt(Cell::v).thenComparingInt(Cell::u));
        input.forEach((cell, material) -> remaining.put(cell, Objects.requireNonNull(material)));
        var result = new ArrayList<Rectangle<T>>();
        while (!remaining.isEmpty()) {
            var first = remaining.firstEntry();
            int u = first.getKey().u(), v = first.getKey().v();
            T material = first.getValue();
            int width = 1, height = 1;
            while (Objects.equals(material, remaining.get(new Cell(u + width, v)))) width++;
            outer: while (true) {
                for (int x = 0; x < width; x++)
                    if (!Objects.equals(material, remaining.get(new Cell(u + x, v + height)))) break outer;
                height++;
            }
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) remaining.remove(new Cell(u + x, v + y));
            result.add(new Rectangle<>(u, v, width, height, material));
        }
        return result;
    }
}
