import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.config.MyConfig.MANGA_DIR;
import static sam.config.MyConfig.MISSING_CHAPTERS_FILE;
import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.FAILED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.swing.SwingPopupShop.showHidePopup;
import static sam.swing.SwingUtils.showErrorDialog;

import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.sqlite.JDBC;

import sam.config.Session;
import sam.console.ANSI;
import sam.io.serilizers.StringWriter2;
import sam.manga.samrock.Renamer;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.ChaptersMeta;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.mangas.MangasMeta;
import sam.sql.querymaker.QueryMaker;
import sam.sql.sqlite.SQLiteDB;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;
import utils.Utils;

public class MangasTools  {
	private static final Session SESSION = Session.getSession(MangasTools.class);

	void finalizeMangarock() {
		Path p  = Paths.get(MANGAROCK_INPUT_DB);
		if(Files.notExists(p)){
			JOptionPane.showMessageDialog(null, p+" not exits");
			return;
		}

		JDialog d = new JDialog(null, "choose process", ModalityType.APPLICATION_MODAL);
		d.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		d.setLayout(new GridLayout(0, 1, 10, 10));
		JButton[] value = {null};
		Function<String, JButton> makeButton = s -> {
			JButton b = new JButton(s);
			b.addActionListener(e -> {
				value[0] = b;
				d.dispose();
			});
			b.setFont(new Font("Consolas", Font.BOLD, 20));
			d.add(b);

			return b;
		};

		JButton insertIds = makeButton.apply("Insert ids"),
				insertSkipIds = makeButton.apply("Insert skip ids"),
				loadTsv = makeButton.apply("Load tsv");

		JCheckBox makebackup = new JCheckBox("create a backup first?");
		makebackup.setSelected(true);
		makebackup.setFont(new Font("Consolas", Font.BOLD, 20));
		d.add(makebackup);

		d.pack();
		d.setLocationRelativeTo(null);
		d.setVisible(true);

		if(value[0] == null){
			System.out.println(red("CANCELLED"));
			return;
		}

		if(makebackup.isSelected()){
			try {
				Path p1 = Paths.get(p+"_1");
				int count = 2;
				while(Files.exists(p1)) p1 = Paths.get(p+"_"+(count++));

				Files.copy(p, p1);

				System.out.println(green("backup created: ")+p1);
			} catch (IOException e) {
				System.err.println("Failed to make a backup of: "+p);
				e.printStackTrace();
				return;
			}
		}

		try (Connection c = DriverManager.getConnection(JDBC.PREFIX + MANGAROCK_INPUT_DB);
				Statement s1 = c.createStatement();
				) {
			c.setAutoCommit(false);

			if(value[0] == insertSkipIds || value[0] == insertIds){
				JTextArea input = new JTextArea(10, 10);
				int optn = JOptionPane.showConfirmDialog(null, new Object[]{"INSERT ID(s) separated by non-numeric charactor(s)", new JScrollPane(input)}, "insert ids", JOptionPane.OK_CANCEL_OPTION);

				if(optn != JOptionPane.OK_OPTION){
					System.out.println(red("CANCELLED"));
					return;
				}
				String text = input.getText();

				if(text == null || text.trim().isEmpty()){
					System.out.println(red("No Input"));
					return;
				}

				List<String> list = Stream.of(input.getText().split("\\D+"))
						.filter(s -> s.matches("\\d+"))
						.collect(Collectors.toList());

				if(value[0] == insertSkipIds){
					System.out.println(yellow("--- insertSkipIds ---"));
					ResultSet rs = s1.executeQuery("SELECT manga_id FROM Favorites");
					StringBuilder b =  new StringBuilder("UPDATE MangaChapter SET read = 1 WHERE manga_id IN(");
					while(rs.next()) {
						String id = rs.getString("manga_id");
						if(!list.contains(id))
							b.append(id).append(',');
					}
					if(b.length() > 1){
						b.setCharAt(b.length() - 1, ')');
						System.out.println("MangaChapter Commit: "+s1.executeUpdate(b.toString()));
					}
				}
				else {
					System.out.println(yellow("--- InsertIds ---"));
					System.out.println("MangaChapter Commit: "+s1.executeUpdate("UPDATE MangaChapter SET read = 1 WHERE manga_id IN("+String.join(",", list)+")"));
				}
			}
			else if(value[0] == loadTsv){
				System.out.println(yellow("--- loadTsv ---"));
				List<String> ids = new ArrayList<>(); 

				Consumer<String> filler = pathString -> {
					Path path = Paths.get(pathString);
					if(Files.notExists(path))
						return;

					try {
						Tsv tsv = Tsv.parse(path);
						Column col = tsv.getColumn("manga_id"); 
						tsv.forEach(r -> ids.add(col.get(r)));
						System.out.println(yellow(path.getFileName()+":  ")+tsv.size());
					} catch (Exception e) {
						System.out.println("failed to read tsv: "+path.getFileName());
					}
				};

				filler.accept(NEW_MANGAS_TSV_FILE);
				filler.accept(UPDATED_MANGAS_TSV_FILE);

				System.out.println("MangaChapter Commit: "+s1.executeUpdate("UPDATE MangaChapter SET read = 1 WHERE manga_id IN("+String.join(",", ids)+")"));
			}
			System.out.println("\n\nDownloadTask Commit: "+s1.executeUpdate("DELETE FROM DownloadTask"));
			c.commit();
		}
		catch (SQLException e) {
			showErrorDialog(null, e);
		}
	}

	private class TempManga {
		final int manga_id;
		final String manga_name;
		final String dir_name;

		public TempManga(ResultSet rs) throws SQLException {
			this.manga_id = rs.getInt(MANGA_ID);
			this.manga_name = rs.getString(MANGA_NAME);
			this.dir_name = rs.getString(DIR_NAME);
		}
		@Override
		public String toString() {
			return "[" + manga_id + ", " + manga_name + ", " + dir_name + "]";
		}
	}

	private class IdName {
		private final int id;
		private final String name;

		public IdName(int id, String name) {
			this.id = id;
			this.name = name;
		}


	}

	/**
	 * list mangas and chapters not in samrock but in mangarock 
	 * @throws IOException 
	 * @throws URISyntaxException 
	 */
	@Deprecated
	boolean analyzeMangaRockFavorites(boolean filter) throws IOException, URISyntaxException{
		System.out.println("\n"+createBanner("Analysing Mangarock Favorites"));

		long maxOldTime = 0;
		if(filter){
			long last = SESSION.contains("-amf-last") ? Long.parseLong(SESSION.getProperty("-amf-last")) : LocalDate.now().minusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.of("+05:30"));
			String lastString = DateTimeFormatter.ofPattern("HH:mm, dd MMM").format(LocalDateTime.ofEpochSecond(last, 0, ZoneOffset.of("+05:30")));

			JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10), false);

			JLabel l = new JLabel("how many days old:  ");
			l.setFont(new Font("Consolas", Font.PLAIN, 20));
			p.add(l);

			JComboBox<Integer> choice = new JComboBox<>(IntStream.range(1, 10).boxed().toArray(Integer[]::new));
			choice.setFont(l.getFont());
			choice.setPreferredSize(new Dimension(70, 30));
			p.add(choice);

			JLabel lastLabel = new JLabel("Last Time:  "+lastString);
			lastLabel.setFont(new Font("Consolas", Font.PLAIN, 20));

			int option = JOptionPane.showOptionDialog(null, new Object[]{p, lastLabel}, "select", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new String[]{"Days", "Last Time", "CANCEL"}, null);

			if(option == JOptionPane.YES_OPTION) {
				System.out.println(yellow("Days: ")+choice.getSelectedItem());
				maxOldTime = LocalDate.now().minusDays((int)choice.getSelectedItem()).atStartOfDay().toEpochSecond(ZoneOffset.of("+05:30"));
			}
			else if(option == JOptionPane.NO_OPTION) {
				System.out.println(yellow("Last Time: ")+lastString);
				maxOldTime = last; 
			}
			else {
				System.out.println(red("CANCELLED"));
				return false;
			}
		}

		if(Files.notExists(Paths.get(MANGAROCK_INPUT_DB))){
			System.out.println(FAILED_BANNER);
			showErrorDialog("File not found: "+ MANGAROCK_INPUT_DB, null);
			return false;
		}

		try {
			Files.deleteIfExists(Paths.get(NEW_MANGAS_TSV_FILE));
			Files.deleteIfExists(Paths.get(UPDATED_MANGAS_TSV_FILE));
		} catch (IOException e) {
			System.out.println(FAILED_BANNER);
			showErrorDialog(null, e);
			return false;
		}

		StringWriter2.appendText(Utils.APP_DATA.resolve("-amf.log"), maxOldTime+"\n");

		try (SamrockDB samrock = new SamrockDB();
				SQLiteDB mangarock = new SQLiteDB(MANGAROCK_INPUT_DB);) {

			List<IdName> mangarockData = new ArrayList<>();
			mangarock.iterate("SELECT manga_id, manga_name FROM Favorites WHERE last_sync >= "+maxOldTime, rs -> mangarockData.add(new IdName(rs.getInt("manga_id"), rs.getString("manga_name"))));

			if(mangarockData.isEmpty()){
				System.out.println(red("Favorites is empty"));
				return false;
			}
			System.out.println("\nin favorite: "+mangarockData.size()+"\n");

			Map<Integer, TempManga> samrockData = new HashMap<>();
			new MangaUtils(samrock).selectAll(rs -> samrockData.put(rs.getInt(MANGA_ID), new TempManga(rs)), MANGA_ID, MANGA_NAME, DIR_NAME);

			List<IdName> updated = new ArrayList<>();
			List<IdName> _new = new ArrayList<>();

			for (IdName in : mangarockData) {
				if(samrockData.containsKey(in.id))
					updated.add(in);
				else
					_new.add(in);
			}

			System.out.printf("new mangas: %s \nupdated mangas: %s\n\n"+ _new.size(), updated.size());

			StringBuilder updatesErrors = new StringBuilder(red("Manga Updates Conflicts \n-------------------------\n"));
			StringBuilder newErrors = new StringBuilder(red("Manga New Conflicts \n------------------------------\n"));
			int updatesErrorsLength = updatesErrors.length();
			int newErrorsLength = newErrors.length();

			for (IdName idName : updated) {
				TempManga manga = samrockData.get(idName.id);
				String dirName = Renamer.mangaDirName(idName.name);

				boolean name = Objects.equals(idName.name, manga.manga_name);
				boolean dir = Objects.equals(dirName, manga.dir_name);

				if(name && dir)
					continue;

				name = !name;
				dir = !dir;

				updatesErrors
				.append("manga_id: ")
				.append(idName.id)
				.append('\t')
				.append(red((name && dir ? "manga_name and dir_name" : name ? "manga_name" : "dir_name")))
				.append(" conflict\n    ")
				.append("samrock -> manga_name: ")
				.append(!name ? manga.manga_name : red(manga.manga_name))
				.append(",  dir_name: ")
				.append(!dir ? manga.dir_name : red(manga.dir_name))
				.append("\n    ")
				.append("mangarock -> manga_name: ")
				.append(!name ? idName.name : red(idName.name))
				.append(",  dir_name: ")
				.append(!dir ? dirName : red(dirName))
				.append('\n');
			}

			if(updatesErrorsLength != updatesErrors.length())
				System.out.println(updatesErrors.append(red("\n-------------------------\n")));

			if(!_new.isEmpty()) {
				Map<String, TempManga> dirNameSamrockData = samrockData.values().stream().collect(Collectors.toMap(t -> t.dir_name, t -> t));
				Map<String, TempManga> mangaNameSamrockData = samrockData.values().stream().collect(Collectors.toMap(t -> t.manga_name, t -> t));

				for (IdName idName : _new) {
					String dirName = Renamer.mangaDirName(idName.name);

					TempManga mangaByName = mangaNameSamrockData.get(idName.name);
					TempManga mangaByDirName = dirNameSamrockData.get(dirName);

					boolean name = mangaByName == null;
					boolean dir = mangaByDirName == null;

					if(name && dir)
						continue;

					name = !name;
					dir = !dir;

					newErrors
					.append(name && dir ? "manga_name and dir_name" : name ? "manga_name" : "dir_name")
					.append('\t')
					.append(red(" Coflicts\n    "))
					.append("samrock -> ")
					.append(name && dir ? "\n        " : "")
					.append(name ? "rowByName: " : "")
					.append(name ? mangaByName : "")
					.append(name && dir ? "\n        " : "")
					.append(dir ? "rowByDirName: " : "")
					.append(dir ? mangaByDirName : "")
					.append("\n    ")
					.append("mangarock -> manga_name: ")
					.append(Arrays.asList(idName.id, idName.name, dirName))
					.append('\n');
				}
			}


			if(newErrorsLength != newErrors.length())
				System.out.println(newErrors.append(red("\n-------------------------\n")));

			if(newErrorsLength != newErrors.length() || updatesErrorsLength != updatesErrors.length())
				return false;

			String lastm = mangarock.executeQuery("SELECT MAX(last_sync) AS value FROM Favorites", rs -> rs.getString("value"));
			SESSION.put("-amf-last", lastm);

			if(!_new.isEmpty()){
				Tsv tsv = new Tsv(MANGA_ID, MANGA_NAME);
				_new.forEach(in -> tsv.addRow(String.valueOf(in.id), in.name));
				tsv.save(Paths.get(NEW_MANGAS_TSV_FILE));
			}

			if(updated.isEmpty())
				return !_new.isEmpty();

			Files.deleteIfExists(Paths.get(MISSING_CHAPTERS_FILE));

			if(filter && JOptionPane.showConfirmDialog(null, "extract chapters?") == JOptionPane.NO_OPTION) 
				return true;

			List<MissingChapter> missingChaps = listMissingChapters(updated, samrock, mangarock);

			if(missingChaps != null && !missingChaps.isEmpty()){
				Tsv tsv = new Tsv(MANGA_ID, MANGA_NAME);
				updated.forEach(in -> tsv.addRow(String.valueOf(in.id), in.name));
				tsv.save(Paths.get(UPDATED_MANGAS_TSV_FILE));

				Map<Integer, String> idNameMap = updated.stream().collect(Collectors.toMap(t -> t.id, t -> t.name));

				Tsv tsv2 = new Tsv(ChaptersMeta.MANGA_ID, MangasMeta.MANGA_NAME, ChaptersMeta.NUMBER, ChaptersMeta.NAME);
				missingChaps.forEach(m -> tsv2.addRow(String.valueOf(m.manga_id), idNameMap.get(m.manga_id),String.valueOf(m.number),m.title));

				tsv2.save(Paths.get(MISSING_CHAPTERS_FILE));
				System.out.println(green(MISSING_CHAPTERS_FILE+" created"));
			}
			else 
				return !_new.isEmpty();
		}
		catch (SQLException | IOException  e) {
			System.out.println(FAILED_BANNER);
			showErrorDialog(null, e);
			return false;
		}
		return true;
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

	public class MissingChapter {
		final int manga_id;
		final double number;
		final String title;

		public MissingChapter(int manga_id, double number, String title) {
			this.manga_id = manga_id;
			this.number = number;
			this.title = title;
		}
	}

	List<MissingChapter> listMissingChapters(List<IdName> idNames, SamrockDB samrock, SQLiteDB mangarock) throws SQLException, URISyntaxException{
		if(idNames.isEmpty()){
			showHidePopup("Empty mangaIds", 1500);
			System.out.println("Empty mangaIds");
			return null;
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
			return null;
		}

		boolean missingSamrock = missingSamrockOption.isSelected();
		boolean unread = unreadOption.isSelected();
		boolean markedUpdated = markedUpdatedOption.isSelected();

		if(!missingSamrock && !unread && !markedUpdated){
			showHidePopup("no options selected", 1500);
			System.out.println("nothing selected");
			return null;
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
			Map<Integer, DoubleStream.Builder> temp = new HashMap<>();

			samrock.iterate(QueryMaker.getInstance().select(ChaptersMeta.MANGA_ID, ChaptersMeta.NUMBER).from(ChaptersMeta.CHAPTERS_TABLE_NAME).where(w -> w.in(ChaptersMeta.MANGA_ID, chapters.stream().mapToInt(t -> t.manga_id).distinct().toArray())).build(),
					rs -> {
						int id = rs.getInt(ChaptersMeta.MANGA_ID);
						DoubleStream.Builder ds = temp.get(id);
						if(ds == null)
							temp.put(id, ds = DoubleStream.builder());
						ds.accept(rs.getDouble(ChaptersMeta.NUMBER));
					});

			Map<Integer, double[]> map = new HashMap<>();
			temp.forEach((id, chStrm) -> {
				if(chStrm != null)
					map.put(id, chStrm.build().sorted().toArray());
			});

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

		return missings;
	}
	public void resetChapters(List<String> mangaIds) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
		if(10 < System.currentTimeMillis())
			throw new IllegalAccessError("not yet implmented");

		if(mangaIds == null || mangaIds.isEmpty()) {
			System.out.println(ANSI.red("no manga_id(s) specified"));
			return;
		}

		Map<Boolean, LinkedHashSet<String>> map = mangaIds.stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.partitioningBy(s -> s.chars().allMatch(Character::isDigit), Collectors.toCollection(LinkedHashSet::new)));

		Optional.ofNullable(map.get(false))
		.filter(l -> !l.isEmpty())
		.ifPresent(list -> System.out.println(ANSI.red("bad inputs: ")+list));

		Set<String> list = map.get(true);
		if(list == null){
			System.out.println(ANSI.red("no manga_id(s) specified"));
			return;
		}

		List<Integer> ids = list.stream().map(Integer::parseInt).collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

		try(SamrockDB db = new SamrockDB()) {
			Map<Integer, String> idDirnameMap = new HashMap<>();
			new MangaUtils(db).select(ids, rs -> idDirnameMap.put(rs.getInt(MANGA_ID), rs.getString(DIR_NAME)), MANGA_ID, DIR_NAME);

			StringBuilder sb = new StringBuilder();
			/* FIXME 
			 * List<ChapterWithMangaId> temp = new ArrayList<>();
			new ChapterUtils(db) .completeUpdate(ids);
			new ChapterUtils(db) .updateChaptersInDB(temp, ids, sb);
			 */

			db.prepareStatementBlock(QueryMaker.getInstance()
					.update(MangasMeta.MANGAS_TABLE_NAME)
					.placeholders(MangasMeta.LAST_UPDATE_TIME)
					.where(w -> w.eqPlaceholder(MangasMeta.MANGA_ID))
					.build(), 
					ps -> {
						for (Integer id : ids) {
							String dirname = idDirnameMap.get(id);

							if(dirname == null) {
								System.out.println(ANSI.red("no manga associated with manga_id: ")+id);
								continue;
							}

							ps.setLong(1, new File(MANGA_DIR, dirname).lastModified());
							ps.setInt(2, id);
							ps.addBatch();
						}
						return ps.executeBatch().length;
					});

			System.out.println(sb);
			db.commit();
		}
	}
}
