package samrock;

import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.swing.SwingUtils.showErrorDialog;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import sam.config.MyConfig;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.mangas.MangasMeta;
import sam.sql.querymaker.QueryMaker;
import sam.sql.sqlite.SQLiteDB;
import sam.tsv.Column;
import sam.tsv.Tsv;


public class FinalizeMangarock {
	List<Integer> mangaids;

	public FinalizeMangarock() {
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
				loadTsv = makeButton.apply("Load tsv"),
				all = makeButton.apply("ALL samrock");

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

		try (SQLiteDB mangarock = new SQLiteDB(MyConfig.MANGAROCK_INPUT_DB);) {

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

				List<Integer> inputList = Stream.of(input.getText().split("\\D+"))
						.filter(s -> s.matches("\\d+"))
						.map(Integer::parseInt)
						.collect(Collectors.toList());

				if(value[0] == insertSkipIds){
					System.out.println(yellow("--- insertSkipIds ---"));
					mangaids = mangarock.stream("SELECT manga_id FROM Favorites", rs -> rs.getInt("manga_id"), e -> {throw new RuntimeException(e);})
							.filter(i -> !inputList.contains(i))
							.collect(Collectors.toList());
				} else {
					System.out.println(yellow("--- InsertIds ---"));
					mangaids = inputList;
				}
			} else if(value[0] == loadTsv){
				System.out.println(yellow("--- loadTsv ---"));
				
				mangaids = Stream.of(NEW_MANGAS_TSV_FILE, UPDATED_MANGAS_TSV_FILE)
				.map(Paths::get)
				.filter(path -> {
					if(Files.notExists(path)) {
						System.out.println(red("not found: ")+path);
						return false;
					}
					return true;
				})
				.map(path -> {
					try {
						return Tsv.parse(path);
					} catch (IOException e1) {
						System.out.println(red("failed to read: ")+path+"  "+e1);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.flatMap(tsv -> {
					Column col = tsv.getColumn("manga_id");
					return tsv.stream().map(col::getInt);
				})
				.collect(Collectors.toList());
			}
			else if(value[0] == all){
				try(SamrockDB samrock = new SamrockDB()) {
					mangaids = samrock.collectToList(QueryMaker.getInstance().select(MangasMeta.MANGA_ID).from(MangasMeta.MANGAS_TABLE_NAME).build(), rs -> rs.getInt(MangasMeta.MANGA_ID));
				}
			}
			if(mangaids != null && !mangaids.isEmpty()) {
				System.out.println("mangaids size: "+mangaids.size());
				String sql = QueryMaker.getInstance().update("MangaChapter")
						.set("read", 1)
						.where(w -> w.in("manga_id", mangaids, false))
						.build();

				System.out.println("MangaChapter Commit: "+mangarock.executeUpdate(sql));
			}

			System.out.println("\n\nDownloadTask Commit: "+mangarock.executeUpdate("DELETE FROM DownloadTask"));
			mangarock.commit();
		}
		catch (SQLException  e) {
			showErrorDialog(null, e);
		}
	}


}
