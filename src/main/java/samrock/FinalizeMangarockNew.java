package samrock;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.manga.samrock.chapters.ChaptersMeta.CHAPTERS_TABLE_NAME;
import static sam.manga.samrock.chapters.ChaptersMeta.MANGA_ID;
import static sam.manga.samrock.chapters.ChaptersMeta.NUMBER;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import sam.console.ANSI;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.ChapterFilter;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MangasMeta;
import sam.sql.JDBCHelper;
import sam.sql.sqlite.SQLiteDB;

public class FinalizeMangarockNew implements Runnable {

	public static void main(String[] args) {
		new FinalizeMangarockNew().run();
	}

	private static class Temp27 {
		final String title;
		final String id;

		public Temp27(ResultSet rs) throws SQLException {
			this.title = rs.getString("title");
			this.id = rs.getString("_id");
		}

		public double number() {
			return MinimalChapter.parseChapterNumber(title).orElse(-1);
		}
	}

	@Override
	public void run() {
		ANSI.enable();
		
		Path mangarock_path  = Paths.get(MANGAROCK_INPUT_DB);
		if(Files.notExists(mangarock_path)){
			JOptionPane.showMessageDialog(null, mangarock_path+" not exits");
			return;
		}

		try(SQLiteDB mangarock = new SQLiteDB(mangarock_path);
				SamrockDB samrock = new SamrockDB();) {

			HashMap<Integer, ChapterFilter> filters = new HashMap<>();
			StringBuilder sb = JDBCHelper.selectSQL(CHAPTERS_TABLE_NAME, MANGA_ID, NUMBER).append(';');
			samrock.iterate(sb.toString(), 
					rs -> filters.computeIfAbsent(rs.getInt(MANGA_ID), mid -> new ChapterFilter(mid, null)).add(rs.getDouble(NUMBER)));

			filters.forEach((s,c) -> c.setCompleted());
			
			sb.setLength(0);
			sb.append("select manga_id, _id, title FROM MangaChapter WHERE read=0 AND manga_id in(");
			filters.forEach((s,t) -> sb.append(s.toString()).append(','));
			sb.setCharAt(sb.length() - 1, ')');
			sb.append(';');

			HashMap<Integer, List<Temp27>> mangarock_chaps = new HashMap<>();

			mangarock.iterate(sb.toString(), rs -> {
				Temp27 t = new Temp27(rs);
				int mangaid = rs.getInt("manga_id");
				if(filters.get(mangaid).test(t.number()))
					mangarock_chaps.computeIfAbsent(mangaid, ___ -> new ArrayList<>()).add(t);
			});

			mangarock_chaps.values().removeIf(List::isEmpty);
			
			if(mangarock_chaps.isEmpty()) {
				System.out.println(ANSI.yellow("NOTHING TO COMMIT"));
				return;
			}
			
			sb.setLength(0);
			
			sb.append("SELECT ").append(MangasMeta.MANGA_ID).append(',').append(MangasMeta.MANGA_NAME).append(" FROM ").append(MangasMeta.MANGAS_TABLE_NAME)
			.append(" WHERE ").append(MangasMeta.MANGA_ID).append(" IN(");
			
			mangarock_chaps.forEach((s,t) -> sb.append(s).append(','));
			sb.setCharAt(sb.length() - 1, ')');
			sb.append(';');
			
			HashMap<Integer, String> names = samrock.collectToMap(sb.toString(), rs -> rs.getInt(MangasMeta.MANGA_ID), rs -> rs.getString(MangasMeta.MANGA_NAME)); 
			
			sb.setLength(0);
			
			ANSI.createBanner("SET READ", sb);
			sb.append('\n');
			
			mangarock_chaps.forEach((s,ts) -> {
				ANSI.yellow(sb, s+": "+names.get(s));
				sb.append('\n');
				ts.forEach(t -> sb.append("  ").append(t.id).append(": ").append(t.title).append('\n'));
				sb.append('\n');
			});
			
			System.out.println(sb);
			sb.setLength(0);
			
			sb.append("UPDATE MangaChapter SET read=1 WHERE _id IN(");
			mangarock_chaps.values().stream()
			.flatMap(List::stream)
			.forEach(t -> sb.append(t.id).append(','));
			sb.setCharAt(sb.length() - 1, ')');
			sb.append(';');
			
			System.out.println("executes: "+mangarock.executeUpdate(sb.toString()));
			mangarock.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
