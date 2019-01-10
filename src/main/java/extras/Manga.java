package extras;

import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.sql.ResultSet;
import java.sql.SQLException;

import sam.manga.samrock.Renamer;

public class Manga {
	public  final int id;
	public  final String name;
	public final String dir_name;
	public final boolean fromMangarock;

	public Manga(ResultSet rs) throws SQLException {
		this.id = rs.getInt(MANGA_ID);
		this.name = rs.getString(MANGA_NAME);
		this.dir_name = rs.getString(DIR_NAME);
		this.fromMangarock = false;
	}
	public Manga(int manga_id, String manga_name, boolean fromMangarock) {
		this.id = manga_id;
		this.name = manga_name;
		this.dir_name = Renamer.mangaDirName(manga_name);
		this.fromMangarock = fromMangarock;
	}
	public Manga(int manga_id, String manga_name, String dir_name, boolean fromMangarock) {
		this.id = manga_id;
		this.name = manga_name;
		this.dir_name = dir_name;
		this.fromMangarock = fromMangarock;
	}

	@Override
	public String toString() {
		return "[" + id + ", " + name + ", " + dir_name + "]";
	}
}
