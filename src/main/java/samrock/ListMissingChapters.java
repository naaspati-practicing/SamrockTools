package samrock;

import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.swing.SwingPopupShop.showHidePopup;

import java.awt.Font;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;

import extras.Manga;
import sam.config.Session;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.nopkg.Junk;
import sam.sql.sqlite.SQLiteDB;
import sam.tsv.Row;
import sam.tsv.Tsv;
import utils.Utils;

public class ListMissingChapters  {
	private List<MissingChapter>  result;
	private static final Session SESSION = Session.getSession(ListMissingChapters.class);

	public ListMissingChapters(List<Manga> idNames, SamrockDB samrock, SQLiteDB mangarock) throws SQLException, URISyntaxException{
		if(idNames.isEmpty()){
			showHidePopup("Empty mangaIds", 1500);
			System.out.println("Empty mangaIds");
			return;
		}
		SESSION.putIfAbsent("listMissingChapters.selectmissingSamrock", "true");
		SESSION.putIfAbsent("listMissingChapters.selectunread", "false");
		SESSION.putIfAbsent("listMissingChapters.selectmarkedUpdated", "false");

		JCheckBox missingSamrockOption = new JCheckBox("Missing From Samrock");
		JCheckBox unreadOption = new JCheckBox("Marked Unread in MangaRock");
		JCheckBox markedUpdatedOption = new JCheckBox("Marked Updated in MangaRock");

		missingSamrockOption.setSelected("true".equals(SESSION.getProperty( "listMissingChapters.selectmissingSamrock").trim()));
		unreadOption.setSelected("true".equals(SESSION.getProperty( "listMissingChapters.selectunread").trim()));
		markedUpdatedOption.setSelected("true".equals(SESSION.getProperty( "listMissingChapters.selectmarkedUpdated").trim()));

		Font font = new Font("Consolas", 1, 20);
		unreadOption.setFont(font);
		missingSamrockOption.setFont(font);
		markedUpdatedOption.setFont(font);

		int option = JOptionPane.showConfirmDialog(null, new Object[]{missingSamrockOption, unreadOption, markedUpdatedOption}, "What to List?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null);

		if(option != JOptionPane.OK_OPTION){
			showHidePopup("Cancelled", 1500);
			return;
		}

		boolean missingSamrock = missingSamrockOption.isSelected();
		boolean unread = unreadOption.isSelected();
		boolean markedUpdated = markedUpdatedOption.isSelected();

		if(!missingSamrock && !unread && !markedUpdated){
			showHidePopup("no options selected", 1500);
			System.out.println("nothing selected");
			return;
		}

		//manga_id -> remove_text
		HashMap<Integer, Pattern> removeFromChapterTitle = new HashMap<>();
		final Path removeFromChapterTsvPath = Utils.APP_DATA.resolve("removeFromChapter.tsv");

		try {
			Tsv temp = Tsv.parse(removeFromChapterTsvPath);
			for (Row row : temp) 
				removeFromChapterTitle.put(row.getInt(MANGA_ID), Pattern.compile(row.get("remove_text"), Pattern.CASE_INSENSITIVE | Pattern.LITERAL));
		} catch (IOException e) {
			System.out.println(red("Failed to to load "+removeFromChapterTsvPath.getFileName()+": "+e+"\n"));
		}

		LinkedList<TempChapter> chapters = new LinkedList<>();

		String joinedIds = idNames.stream().map(in -> String.valueOf(in.id)).collect(Collectors.joining(","));

		mangarock.iterate("SELECT title, manga_id, read, _id FROM MangaChapter WHERE manga_id IN("+joinedIds+")",
				rs -> {
					int manga_id = rs.getInt("manga_id");
					String title = rs.getString("title");

					Pattern removeText = removeFromChapterTitle.get(manga_id);

					if(removeText != null)
						title = removeText.matcher(title).replaceFirst("").trim();

					chapters.add(new TempChapter(rs.getInt("_id"), manga_id, title, rs.getBoolean("read")));
				});

		List<MissingChapter> missings = new ArrayList<>();

		BiConsumer<OptionalDouble, TempChapter> addMissing = (number, ch)-> {
			if(number != null && number.isPresent())
				missings.add(new MissingChapter(ch.manga_id, number.getAsDouble(), ch.title));
			else
				System.out.println(red("bad chapter: ")+ch);
		};
		Consumer<TempChapter> addMissing2 = ch -> addMissing.accept(MinimalChapter.parseChapterNumber(ch.title), ch);

		if(unread){
			chapters.removeIf(ch -> {
				if(!ch.read) {
					addMissing2.accept(ch);
					return true;
				}
				return false;
			});
			System.out.println("unread loaded: "+missings.size());
		}

		if(markedUpdated && !chapters.isEmpty()) {
			IntStream.Builder builder = IntStream.builder();

			mangarock.iterate("SELECT cids FROM MangaUpdate WHERE _id IN("+joinedIds+")",
					rs ->  Stream.of(rs.getString("cids").split(",")).map(String::trim).mapToInt(Integer::parseInt).forEach(builder::accept));

			int[] cids = builder.build().sorted().toArray();

			int size = missings.size();

			chapters.removeIf(ch -> {
				if(Arrays.binarySearch(cids, ch.chapter_id) >= 0) {
					addMissing2.accept(ch);
					return true;
				}
				return false;
			});
			System.out.println("marked loaded: "+(missings.size() - size));
		}
		if(missingSamrock && !chapters.isEmpty()){
			Junk.notYetImplemented();
			Map<Integer, double[]> map = null; //FIXME = new ChapterUtils(samrock) .chapterNumbers().byMangaIds(Iterables.map(chapters, c -> c.manga_id));

			for (TempChapter ch : chapters) {
				OptionalDouble number = MinimalChapter.parseChapterNumber(ch.title);
				double[] has = map.get(ch.manga_id);
				if(!number.isPresent() || has == null)
					addMissing.accept(OptionalDouble.empty(), ch);
				else if(Arrays.binarySearch(has, number.getAsDouble()) < 0)
					addMissing.accept(number, ch);
			}
		}
		int[] ids = missings.stream().mapToInt(t -> t.manga_id).sorted().distinct().toArray();

		boolean firstLine[] = {false};
		idNames.removeIf(in -> {
			if(Arrays.binarySearch(ids, in.id) < 0) {
				if(!firstLine[0]) {
					System.out.println(yellow("Mangas with no missing chapters: "));
					firstLine[0] = true;
				}
				System.out.printf("%-10s%s\n", in.id, in.name);
				return true;
			}
			return false;
		});

		if(firstLine[0])
			System.out.println();

		if(!missings.isEmpty()) {
			Map<Integer, Long> map = missings.stream().collect(Collectors.groupingBy(m -> m.manga_id, Collectors.counting()));
			idNames.forEach(in -> System.out.println(in.name+": "+yellow(map.get(in.id))));
			System.out.println();
		}

		this.result = missings;
	}

	private class TempChapter {
		final int chapter_id;
		final int manga_id;
		final String title;
		final boolean read;

		public TempChapter(int chapter_id, int manga_id, String title, boolean read) {
			this.manga_id = manga_id;
			this.title = title;
			this.read = read;
			this.chapter_id = chapter_id;
		}

		@Override
		public String toString() {
			return "TempChapter [chapter_id=" + chapter_id + ", manga_id=" + manga_id + ", title=" + title + ", read="
					+ read + "]";
		}

	}
	public List<MissingChapter> getResult() {
		return result;
	}
}
