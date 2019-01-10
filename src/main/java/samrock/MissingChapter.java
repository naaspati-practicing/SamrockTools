package samrock;

public class MissingChapter {
    public final int manga_id;
    public final double number;
    public final String title;

    public MissingChapter(int manga_id, double number, String title) {
        this.manga_id = manga_id;
        this.number = number;
        this.title = title;
    }
}