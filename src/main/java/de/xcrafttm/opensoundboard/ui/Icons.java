package de.xcrafttm.opensoundboard.ui;

/**
 * Small hand-drawn pixel icons, rendered with plain fills so they stay crisp at every GUI scale
 * and match Minecraft's pixel look. Each icon is a bitmap where {@code #} is a lit pixel.
 */
public enum Icons {

    PLAY(
            "#.....",
            "###...",
            "#####.",
            "######",
            "#####.",
            "###...",
            "#....."),
    PAUSE(
            "##.##",
            "##.##",
            "##.##",
            "##.##",
            "##.##",
            "##.##",
            "##.##"),
    STOP(
            "######",
            "######",
            "######",
            "######",
            "######",
            "######"),
    SKIP_BACK(
            "#....#",
            "#...##",
            "#..###",
            "#.####",
            "#..###",
            "#...##",
            "#....#"),
    SKIP_FORWARD(
            "#....#",
            "##...#",
            "###..#",
            "####.#",
            "###..#",
            "##...#",
            "#....#"),
    LOOP(
            "....#..",
            ".#####.",
            "#...#..",
            "#.....#",
            "..#...#",
            ".#####.",
            "..#...."),
    START_HERE(
            "#......",
            "#.#....",
            "#.###..",
            "#.####.",
            "#.###..",
            "#.#....",
            "#......"),
    STAR(
            "...#...",
            "..###..",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            "##...##"),
    FOLDER(
            "###.....",
            "#..#####",
            "#......#",
            "#......#",
            "#......#",
            "########"),
    BACK(
            "..#....",
            ".#.....",
            "#######",
            ".#.....",
            "..#...."),
    REFRESH(
            "..###.#",
            ".#...##",
            "#...###",
            "#......",
            "#.....#",
            ".#...#.",
            "..###.."),
    SETTINGS(
            "...#...",
            ".#####.",
            ".#...#.",
            "##.#.##",
            ".#...#.",
            ".#####.",
            "...#..."),
    DOWNLOAD(
            "..###..",
            "..###..",
            "#######",
            ".#####.",
            "..###..",
            "...#...",
            ".......",
            "#######"),
    CLOSE(
            "#.....#",
            ".#...#.",
            "..#.#..",
            "...#...",
            "..#.#..",
            ".#...#.",
            "#.....#"),
    SEARCH(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            "....##.",
            ".....##"),
    CHECK(
            "......#",
            ".....##",
            "#...##.",
            "##.##..",
            ".###...",
            "..#...."),
    ARROW_UP(
            "..#..",
            ".###.",
            "#####"),
    ARROW_DOWN(
            "#####",
            ".###.",
            "..#.."),
    CHEVRON_LEFT(
            "..#",
            ".#.",
            "#..",
            ".#.",
            "..#"),
    CHEVRON_RIGHT(
            "#..",
            ".#.",
            "..#",
            ".#.",
            "#.."),
    SLIDERS(
            ".#.....",
            "#######",
            ".#.....",
            ".....#.",
            "#######",
            ".....#.",
            "..#....",
            "#######",
            "..#...."),
    SPEAKER(
            "...#...",
            "..##.#.",
            "####..#",
            "####..#",
            "####..#",
            "..##.#.",
            "...#..."),
    WHEEL(
            "..###..",
            ".#.#.#.",
            "#..#..#",
            "#######",
            "#..#..#",
            ".#.#.#.",
            "..###.."),
    PALETTE(
            ".#####.",
            "#.....#",
            "#.#.#.#",
            "#.....#",
            "#..####",
            "#..#...",
            ".##...."),
    LOG(
            "#######",
            ".......",
            "#####..",
            ".......",
            "######.",
            ".......",
            "###...."),
    NOTE(
            "...####",
            "...####",
            "...#..#",
            "...#..#",
            ".###.##",
            "#####..",
            ".###...");

    public final int width;
    public final int height;
    /** Horizontal runs per row as {@code [row, startX, length]} triples. */
    private final int[] runs;

    Icons(String... rows) {
        this.height = rows.length;
        int maxWidth = 0;
        int[] buffer = new int[rows.length * 24];
        int count = 0;
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            maxWidth = Math.max(maxWidth, line.length());
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') col++;
                buffer[count++] = row;
                buffer[count++] = start;
                buffer[count++] = col - start;
            }
        }
        this.width = maxWidth;
        this.runs = java.util.Arrays.copyOf(buffer, count);
    }

    /** Draw at pixel scale 1 with the top-left corner at (x, y). */
    void draw(UiCanvas c, int x, int y, int argb) {
        for (int i = 0; i < runs.length; i += 3) {
            c.fillRect(x + runs[i + 1], y + runs[i], runs[i + 2], 1, argb);
        }
    }
}
