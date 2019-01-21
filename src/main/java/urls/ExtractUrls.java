package urls;

import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.manga.samrock.urls.MangaUrlsMeta.MANGAFOX;
import static sam.manga.samrock.urls.MangaUrlsMeta.MANGAHERE;
import static sam.manga.samrock.urls.MangaUrlsMeta.MANGA_ID;

import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.UIManager;

import org.jsoup.Jsoup;

import sam.collection.Iterables;
import sam.console.ANSI;
import sam.console.VT100;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.urls.MangaUrlsUtils;
import sam.manga.samrock.urls.MangaUrlsUtils.MangaUrl;
import sam.myutils.Checker;
import sam.swing.SwingClipboard;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;

public class ExtractUrls {
	private boolean returnFalse = false;
	private Tsv newMangasTsv, updatedMangasTsv;

	/**
	 * extract urls from Manga Index - Manga Fox!.html, samrock database for new mangas and updated mangas 
	 * @param b 
	 */
	public boolean extractUrls(boolean fillUrls){
		System.out.println();
		System.out.println(createBanner("Extracting urls"));
		System.out.println();

		newMangasTsv = readTsv(NEW_MANGAS_TSV_FILE);
		if(returnFalse)
			return false;

		updatedMangasTsv = readTsv(UPDATED_MANGAS_TSV_FILE);
		if(returnFalse)
			return false;

		if((newMangasTsv == null || newMangasTsv.isEmpty()) && (updatedMangasTsv == null || updatedMangasTsv.isEmpty())){
			System.out.println("nothing found");
			return false; 
		}
		boolean returnValue = true;

		//checking updated mangas
		if(updatedMangasTsv == null || updatedMangasTsv.isEmpty())
			System.out.println(red("updated manga is empty"));
		else{
			try (SamrockDB samrock = new SamrockDB()) {
				process(samrock);
				updatedMangasTsv.save(Paths.get(UPDATED_MANGAS_TSV_FILE));
				System.out.println(green("updatedManga filled"));
			}
			catch (SQLException|IOException  e) {
				System.out.println("updated manga url listing failed : "+e);
				e.printStackTrace();
				returnValue = false;
			}
		}
		//checking new mangas
		if(!checkNewMangas())
			return false;

		if(returnValue && fillUrls)
			return new FillUrls().fillUrls(newMangasTsv, updatedMangasTsv);

		return returnValue;
	}

	private void process(SamrockDB samrock) throws SQLException {
		Column manga_id = updatedMangasTsv.getColumn(MANGA_ID);
		Column fox = updatedMangasTsv.getColumn(MANGAFOX);
		Column here = updatedMangasTsv.getColumn(MANGAHERE);

		Map<Integer, MangaUrl> samrockUrls = new MangaUrlsUtils(samrock).getMangaUrls(Stream.of(updatedMangasTsv, newMangasTsv).filter(Objects::nonNull).flatMap(Tsv::stream).mapToInt(manga_id::getInt).toArray()).stream().collect(Collectors.toMap(MangaUrl::getMangaId, m -> m));
		boolean nullFound = false;

		for (Row row : updatedMangasTsv) {
			MangaUrl url = samrockUrls.get(manga_id.getInt(row));
			if(url != null) {
				String f  = url.getMangafoxUrl();
				String h = url.getMangahereUrl();

				fox.set(row, f);
				here.set(row, h);

				nullFound = nullFound || f == null || h == null; 
			} else 
				nullFound = true;
		}
		if(nullFound) {
			updatedMangasTsv.sort((r1, r2) -> {
				String r1f = fox.get(r1);
				String r2f = fox.get(r2);
				String r1h = here.get(r1);
				String r2h = here.get(r2);

				boolean b1 = r1f == null || r1h == null || r1f.trim().isEmpty() || r1h.trim().isEmpty();
				boolean b2 = r2f == null || r2h == null || r2f.trim().isEmpty() || r2h.trim().isEmpty();

				if(b1 && b2)
					return 0;

				if(b2)
					return -1;

				if(b1)
					return 1;

				return r1f.compareTo(r2f);
			});
		}

		if(newMangasTsv != null) {
			for (Row row : newMangasTsv) {
				MangaUrl url = samrockUrls.get(manga_id.getInt(row));

				if(url != null) {
					fox.set(row, url.getMangafoxUrl());
					here.set(row, url.getMangahereUrl());
				} 
			}
		}
	}

	private boolean checkNewMangas() {
		if(newMangasTsv == null || newMangasTsv.isEmpty())
			System.out.println(red("new manga is empty"));
		else{
			Column manga_name = newMangasTsv.getColumn(MANGA_NAME);
			Column fox = newMangasTsv.getColumn(MANGAFOX);
			Column here = newMangasTsv.getColumn(MANGAHERE);

			try {
				if(returnFalse)
					return false;

				JDialog dialog = new JDialog((JFrame)null, "enter url", true);
				dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
				dialog.setLayout(new GridBagLayout());
				dialog.setModalityType(ModalityType.APPLICATION_MODAL);

				Font font = new Font("Consolas", Font.PLAIN, 20);
				UIManager.put("Label.font", font);
				UIManager.put("TextField.font", font);

				GridBagConstraints g = new GridBagConstraints();
				g.insets = new Insets(5, 5, 5, 5);

				g.gridx = 0;
				g.gridy = 0;
				g.anchor = GridBagConstraints.NORTHWEST;
				JLabel title = new JLabel();
				title.setFont(font.deriveFont(Font.BOLD, 15f));
				g.gridwidth = GridBagConstraints.REMAINDER;
				dialog.add(title, g);
				g.gridy = 1;
				g.gridwidth = 1;
				dialog.add(new JLabel("Fanfox"), g);

				g.gridx = 0;
				g.gridy = 2;
				dialog.add(new JLabel("mangaHere"), g);

				g.gridx = 1;
				g.gridy = 1;
				g.fill = GridBagConstraints.HORIZONTAL;
				g.gridwidth = GridBagConstraints.REMAINDER;
				g.weightx = 1;

				JTextField fanfox = new JTextField();
				dialog.add(fanfox, g);

				g.gridy = 2;
				JTextField mangahere = new JTextField();
				dialog.add(mangahere, g);

				g.gridx = 0;
				g.gridy = 3;
				g.anchor = GridBagConstraints.SOUTHEAST;
				g.fill = GridBagConstraints.NONE;
				g.weightx = 0;

				String[] result = {null, null};

				JButton ok = new JButton("OK");
				dialog.add(ok, g);
				ok.addActionListener(e -> {
					result[0] = fanfox.getText();
					result[1] = mangahere.getText();
					dialog.setVisible(false);
				});

				dialog.setSize(600, 200);
				dialog.setLocationRelativeTo(null);
				dialog.setResizable(false);

				for (Row row : newMangasTsv) {
					result[0] = null;
					result[1] = null;
					fanfox.setText(fox.get(row));
					mangahere.setText(here.get(row));

					String name = manga_name.get(row);
					title.setText(name);
					SwingClipboard.setString(name);
					dialog.setVisible(true);

					fox.set(row, result[0]);
					here.set(row, result[1]);
				}

				newMangasTsv.save(Paths.get(NEW_MANGAS_TSV_FILE));
				System.out.println(green("newManga filled"));
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		return true;
	}

	private class Urls {
		private final Map<String, String> original;
		private final Map<String, String> laveraged;

		public Urls() {
			original = Collections.emptyMap();
			laveraged = Collections.emptyMap();
		}
		public Urls(Map<String, String> original) {
			if(Checker.isEmpty(original)) {
				this.original = Collections.emptyMap();
				laveraged = Collections.emptyMap();
			} else {
				this.original = original;
				this.laveraged = new HashMap<>();
				original.forEach((s,t) -> laveraged.put(laveraged(s), t));
			}
		}
	}

	private StringBuilder sb = new StringBuilder();
	private String laveraged(String s) {
		sb.setLength(0);

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if(Character.isAlphabetic(c) || (c >= '0' && c <= '9')) 
				sb.append(Character.toLowerCase(c));
		}
		return sb.toString();
	}

	private Urls mangahere;
	private Urls mangahere(Iterable<String> names) {
		if(mangahere != null)
			return mangahere;
		Map<String, String>  map = new HashMap<>();
		VT100.save_cursor();

		for (String name : names) {
			try {
				String url  = "http://www.mangahere.cc/search?title=".concat(URLEncoder.encode(name, "utf-8"));
				VT100.unsave_cursor();
				VT100.erase_down();
				System.out.println(ANSI.yellow("searching: ")+url);
				Jsoup.parse(new URL(url), 60000)
				.getElementsByTag("a")
				.forEach(e -> {
					String href = e.attr("href"); 
					if(href.startsWith("/manga/")) 
						map.computeIfAbsent(e.attr("title"), ss -> "http://www.mangahere.cc".concat(href));
				});
			} catch (Exception e) {
				System.out.println(ANSI.red("failed: ")+e);
				VT100.save_cursor();		
			}				
		}

		mangahere = new Urls(map);
		return mangahere;
	} 

	private Tsv readTsv(String path) {
		Path p = Paths.get(path);
		if(Files.exists(p)){
			try {
				Tsv  tsv = Tsv.parse(p);
				tsv.addColumnIfAbsent(MANGAFOX);
				tsv.addColumnIfAbsent(MANGAHERE);

				Column mangahereC = tsv.getColumn(MANGAHERE);
				Column nameC = tsv.getColumn(MANGA_NAME);
				Urls urls = mangahere(Iterables.map(tsv, nameC::get));

				tsv.forEach(r -> {
					String s = mangahereC.get(r);
					if(!Checker.isEmptyTrimmed(s))
						return;

					String name = nameC.get(r);
					s = urls.original.get(name);

					if(s != null) {
						System.out.println(ANSI.yellow("name: ")+name+ANSI.yellow(",  url: ")+s);
						mangahereC.set(r, s);
						return;
					} else {
						String name2 = laveraged(name);
						s = urls.laveraged.get(name2);

						if(s != null) {
							System.out.println(ANSI.yellow("name: ")+name + ANSI.yellow(" ("+name2+")")+ANSI.yellow(",  url: ")+s);
							mangahereC.set(r, s);
							return;
						}	
					}
					System.out.println(ANSI.red("not found: ")+name);
				});

				return tsv;
			} catch (IOException e) {
				System.out.println("failed parsing: "+path+"\tError: "+e);
				returnFalse = true;
			}
		}
		else
			System.out.println(path +red("\tdoes'nt exists"));

		return null;
	}

}
