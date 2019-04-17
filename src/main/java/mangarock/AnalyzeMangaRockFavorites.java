package mangarock;

import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.FAILED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.red;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.swing.SwingUtils.showErrorDialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;

import extras.Manga;
import sam.config.MyConfig;
import sam.io.serilizers.IntSerializer;
import sam.io.serilizers.StringIOUtils;
import sam.manga.mangarock.MangarockDB;
import sam.manga.samrock.SamrockDB;
import sam.sql.JDBCHelper;
import sam.tsv.Tsv;
import utils.Utils;

public class AnalyzeMangaRockFavorites {
	public boolean getResult() throws URISyntaxException, IOException {
		System.out.println("\n"+createBanner("Analysing Mangarock Favorites"));

		int durationInSecond = selectTime();

		if(durationInSecond <= 0) {
			System.out.println("CANCELLED");
			return false;
		}

		if(Files.notExists(Paths.get(MyConfig.MANGAROCK_INPUT_DB)) &&Files.notExists(Paths.get(MyConfig.MANGAROCK_DB_BACKUP))  ){
			System.out.println(FAILED_BANNER);
			showErrorDialog("File not found: \n"+ MyConfig.MANGAROCK_INPUT_DB+"\n"+MyConfig.MANGAROCK_DB_BACKUP, null);
			return false;
		}
		if(!delete())
			return false;

		try (SamrockDB samrock = new SamrockDB();
				MangarockDB mangarock0 = new MangarockDB();) {

			Path lastFavoritesPath = Utils.APP_DATA.resolve("last-favorites.intarray");
			int[] lastFavorites = new IntSerializer().readArray(lastFavoritesPath);
			int[] favorites = mangarock0.stream("select manga_id from Favorites", rs -> rs.getInt(1)).mapToInt(Integer::intValue).toArray();
			int[] mangaupdate = mangarock0.stream("select _id from MangaUpdate where time <= "+durationInSecond, rs -> rs.getInt(1)).mapToInt(Integer::intValue).toArray();

			Arrays.sort(mangaupdate);
			StringBuilder sb = new StringBuilder("select manga_id, manga_name from Favorites where manga_id in(");
			int n = 0;
			for (int id : favorites) {
				if(Arrays.binarySearch(mangaupdate, id) >= 0 || Arrays.binarySearch(lastFavorites, id) < 0) {
					sb.append(id).append(',');
					n++;
				}
			}
			if(n == 0){
				System.out.println(red("no new/updated data in Favorites"));
				return false;
			}
			sb.setCharAt(sb.length() - 1, ')');

			System.out.println("\nin favorite: "+n+"\n");
			
			List<Manga> mangarockData = mangarock0.collectToList(sb.toString(), rs -> new Manga(rs.getInt(1), rs.getString(2), true));
			List<Manga> samrockData = samrock.collectToList(JDBCHelper.selectSQL(MANGAS_TABLE_NAME, MANGA_ID, MANGA_NAME, DIR_NAME).append(';').toString(), Manga::new);
			
			int[] samrockIds = new int[samrockData.size()];
			for (int i = 0; i < samrockIds.length; i++) 
				samrockIds[i] = samrockData.get(i).id;

			Arrays.sort(samrockIds);

			List<Manga> updated = new ArrayList<>();
			List<Manga> nnew = new ArrayList<>();

			for (Manga m : mangarockData) {
				if(Arrays.binarySearch(samrockIds, m.id) < 0)
					nnew.add(m);
				else
					updated.add(m);
			}

			System.out.printf("new mangas: %s \nupdated mangas: %s\n\n", nnew.size(), updated.size());

			MangaCheck check = new MangaCheck(nnew, updated, samrockData);

			if(check.newError || check.updatesError)
				return false;

			save(nnew, NEW_MANGAS_TSV_FILE);
			save(updated, UPDATED_MANGAS_TSV_FILE);

			Arrays.sort(favorites);
			new IntSerializer().write(favorites, lastFavoritesPath);

			return !nnew.isEmpty() || !updated.isEmpty();
		} catch (SQLException | IOException  e) {
			System.out.println(FAILED_BANNER);
			showErrorDialog(null, e);
			return false;
		}
	}

	private void save(List<Manga> data, String path) throws IOException {
		if(!data.isEmpty()){
			Tsv tsv = new Tsv(MANGA_ID, MANGA_NAME);
			data.forEach(in -> tsv.addRow(String.valueOf(in.id), in.name));
			tsv.save(Paths.get(path));
		}
	}

	private boolean delete() {
		try {
			Files.deleteIfExists(Paths.get(NEW_MANGAS_TSV_FILE));
			Files.deleteIfExists(Paths.get(UPDATED_MANGAS_TSV_FILE));
		} catch (IOException e) {
			System.out.println(FAILED_BANNER);
			showErrorDialog(null, e);
			return false;
		}
		return true;
	}

	private static final LocalDateTime NOW = LocalDateTime.now();

	class Temp {
		final String dateS;
		final LocalDate date;
		final int days;

		public Temp(String date) {
			this.dateS = date;
			this.date = LocalDate.parse(date);
			this.days = days(this.date); 
		}
		private int days(LocalDate ld) {
			long d = Math.abs(Duration.between(ld.atStartOfDay(), NOW).toDays());
			if(d < 1)
				return 1;
			return (int)d;
		}
	}

	public int selectTime() throws IOException {
		Vector<Temp> data = Files.lines(Utils.APP_DATA.resolve("-amf.dat")).filter(s -> !s.isEmpty()).distinct().map(Temp::new).collect(Collectors.toCollection(Vector::new));
		Collections.reverse(data);

		JDialog dialog = new JDialog((Frame)null);
		JList<Temp> jlist = new JList<>(data);
		jlist.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ListCellRenderer<? super Temp> renderer = jlist.getCellRenderer();
		jlist.setFont(new Font("Consolas", Font.PLAIN, 20));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM");

		jlist.setCellRenderer(new ListCellRenderer<Temp>() {
			@Override
			public Component getListCellRendererComponent(JList<? extends Temp> list, Temp value, int index, boolean isSelected, boolean cellHasFocus) {
				JLabel l = (JLabel) renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				l.setText(value.date.format(formatter)+" ("+value.days+")");
				l.setBorder(new LineBorder(Color.LIGHT_GRAY));
				return l;
			}
		});

		JPanel panel = new JPanel(new FlowLayout(), false);
		JTextField field = new JTextField(5);
		panel.add(new JLabel("days: "));
		panel.add(field);
		JButton btn = new JButton("OK");

		jlist.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				field.setText(String.valueOf(jlist.getSelectedValue().days));
				if(e.getClickCount() > 1) 
					btn.doClick();
			}
		});

		dialog.add(new JScrollPane(jlist));
		int selected[] = {-1};

		btn.addActionListener(e -> {
			try {
				int n = Integer.parseInt(field.getText());
				if(n < 1)
					n = 1;
				selected[0] = n;
				dialog.dispose();
			} catch (NumberFormatException|NullPointerException e2) {
				System.out.println("failed parsing number: "+field.getText()+"  "+e);
			}
		});
		panel.add(btn);
		dialog.add(panel, BorderLayout.SOUTH);

		dialog.setSize(300, 500);
		dialog.setLocationRelativeTo(null);
		dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		dialog.setModalityType(ModalityType.APPLICATION_MODAL);
		jlist.getSelectionModel().setSelectionInterval(0, 0);
		field.setText(String.valueOf(jlist.getSelectedValue().days));

		dialog.setVisible(true);

		if(selected[0] == -1)
			return -1;

		LocalDate date = NOW.toLocalDate();
		if(data.stream().noneMatch(t -> t.date.equals(date))) 
			StringIOUtils.appendText(date+"\n", Utils.APP_DATA.resolve("-amf.dat"));

		return (int) (selected[0] * Duration.ofDays(1).getSeconds());
	}
}
