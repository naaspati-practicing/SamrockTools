import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.config.MyConfig.MANGAROCK_INPUT_DIR;
import static sam.config.MyConfig.MISSING_CHAPTERS_FILE;
import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.swing.SwingPopupShop.showHidePopup;
import static sam.swing.SwingUtils.filePathInputOptionPane;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.sqlite.JDBC;

import javafx.application.Application;
import mangarock.AnalyzeMangaRockFavorites;
import mangarock.Grouping;
import mangarock.RemoveRepeatedFromMangarockFavorites;
import mangarock.UpdateWithLastSync;
import sam.config.Session;
import sam.console.ANSI;
import sam.myutils.System2;
import sam.string.StringUtils;
import sam.tsv.Column;
import sam.tsv.Tsv;
import samrock.AddNewManga;
import samrock.BuIdTools;
import samrock.ChangeMangaIds;
import samrock.CheckMangaFolder;
import samrock.FinalizeMangarockNew;
import samrock.ResetChapters;
import urls.ExtractUrls;
import urls.FillUrls;
import urls.Urls;
import utils.Utils;

public class Main  {
	private static final Session SESSION = Session.getSession(Main.class);

	public static final String VERSION = System2.lookup("APP_VERSION");
	public static final String PROGRAM_NAME = System2.lookup("PROGRAM_NAME");

	public static void main(String[] args) throws Exception {
		new Main(args);
	}

		boolean help;
		boolean version;
		boolean extractAllBakaUrl;
		boolean analyzeMangaRockFavorites;
		boolean batchDownloadThumbsFromBuid;
		boolean clean;
		boolean createMissingBuIdList;
		boolean runCollective;

		boolean checkFoldersNotConverted;
		boolean downloadMissingThumbs;
		boolean downloadThumbsFromBuid;
		boolean downloadThumbsBoth;
		boolean moveThumbs;
		boolean missingThumbsCheck;
		boolean emptyThumbsResultFolder;
		boolean extractUrls;
		boolean fillBuIds;
		boolean finalizeMangarock;
		boolean fillUrls;
		boolean moveThumbsToId;
		boolean extractRemoveBakaUrl;
		boolean showMissingChapters;
		boolean last_sync;
		boolean resetChapter;

		boolean changeMangaIds;

		boolean update_with_last_sync;

		boolean remove_repeated_favorites;
		boolean add_new_manga;
		boolean urls;
		boolean group;

	List<String> mangaIds = new ArrayList<>();

	public Main(String[] args2) throws Exception {
		parse(args2);

		if(args2.length == 0 || help) {
			printHelp();
			return;
		}
		if(version) {
			System.out.println(ANSI.yellow("version: ")+VERSION);
			return;
		}

		Utils.init() ;
		boolean exit = true;

		if(checkFoldersNotConverted)
			new CheckMangaFolder().checkFoldersNotConverted();
		else if(extractUrls)
			new ExtractUrls().extractUrls(false);
		else if(fillUrls)
			new FillUrls().fillUrls();
		else if(missingThumbsCheck)
			new ThumbsTools().listMissingThumbs();
		else if(analyzeMangaRockFavorites)
			new AnalyzeMangaRockFavorites().getResult();
		else if(runCollective){
			System.out.println("-col");
			boolean 
			b = new AnalyzeMangaRockFavorites().getResult();  
			b =  b && new ExtractUrls().extractUrls(true);

			JOptionPane.showMessageDialog(null, !b ? "Failed" : "DONE");
		}
		else if(downloadThumbsBoth){
			ThumbsTools tt = new ThumbsTools();
			tt.batchDownloadThumbsFromBuid();
			System.out.println("\n");
			tt.downloadMissingThumbs();
		}
		else if(downloadMissingThumbs)
			new ThumbsTools().downloadMissingThumbs();
		else if(batchDownloadThumbsFromBuid)
			new ThumbsTools().batchDownloadThumbsFromBuid();
		else if(downloadThumbsFromBuid){
			String buid = JOptionPane.showInputDialog("bu_id");

			if(buid == null || !buid.matches("\\d+")){
				JOptionPane.showInternalMessageDialog(null, "invalid buid input", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			String manga_id = JOptionPane.showInputDialog("manga_id");

			if(manga_id == null || !manga_id.matches("\\d+")){
				JOptionPane.showInternalMessageDialog(null, "invalid manga_id input", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			Tsv data = new Tsv("bu_id", "manga_id");
			data.addRow(buid, manga_id);
			new ThumbsTools().downloadThumbsFromBuid(data);
		}
		else if(createMissingBuIdList)
			new BuIdTools().createMissingBuIdList();
		else if(fillBuIds)
			new BuIdTools().fillBuIds();
		else if(moveThumbsToId)
			new ThumbsTools().moveThumbsToId();
		else if(emptyThumbsResultFolder)
			new ThumbsTools().emptyThumbsResultFolder();
		else if(moveThumbs)
			new ThumbsTools().moveThumbs();
		else if(extractAllBakaUrl)
			extractBakaupdateUrls(true);
		else if(extractRemoveBakaUrl)
			extractBakaupdateUrls(false);
		else if(clean)
			clean();
		else if(finalizeMangarock)
			new FinalizeMangarockNew().run();
		else if(last_sync   )
			lastSyncSort();
		else if(resetChapter)
			new ResetChapters(mangaIds);
		else if(changeMangaIds)
			new ChangeMangaIds();
		else if(update_with_last_sync)
			new UpdateWithLastSync();
		else if(remove_repeated_favorites)
			new RemoveRepeatedFromMangarockFavorites();
		else if(add_new_manga) {
			Application.launch(AddNewManga.class, new String[0]);
			exit = false;
		} else if(urls) {
			Application.launch(Urls.class, mangaIds.toArray(new String[0]));
			exit = false;
		} else if(group) {
			new Grouping().call();
			exit = true;
		}
		else {
			System.out.println(red("failed to reconize command: ")+Arrays.toString(args2));
			return;
		}
		if(exit) {
			System.out.println("\n"+FINISHED_BANNER);
			System.exit(0);
		}
	}
	private void printHelp() {
		String[][] help = {
				{"-h, --help", "help"},
				{"-v, --version", "version"},
				{"-abaka, --extractAllBakaUrl", ""},
				{"-amf, --analyzeMangaRockFavorites", "check mangarock.Favorites and samrock to find missing chapter and mangas not in samrock"},
				{"-bdtfb, --batchDownloadThumbsFromBuid", "batch download thumbs from http://mcd.iosphe.re/"},
				{"-cl, --clean", "delete files created by samrock tools     "},
				{"-cmbil, --createMissingBuIdList", "list mangas whose bu_id are missing"},
				{"-col, --runCollective", "runs analyzeMangaRockFavorites, extractUrls, fillUrls, jsonCheck "},
				{"-cfnc, --check-folders-not-converted", "Check Folders Not Converted"},
				{"-dmt, --downloadMissingThumbs", "download missing thumbs from manga and mangaupdates"},
				{"-dtfb, --downloadThumbsFromBuid", "download single manga's thumbs from http://mcd.iosphe.re/"},
				{"-dbt, --downloadThumbsBoth", "download missings and bu_id file thumbs"},
				{"-mt, --moveThumbs", "move thumbs"},
				{"-mtc, --missingThumbsCheck", "check for missing thumbs"},
				{"-etrf, --emptyThumbsResultFolder", "empty result folder created by move thumbs "},
				{"-eu, --extractUrls", "extract urls from Manga Index - Manga Fox!.html, samrock database for new mangas and updated mangas"},
				{"-fbi, --fillBuIds", "fill bu_id in samrock"},
				{"-fmr, --finalizeMangarock", ""},
				{"-fu, --fillUrls", "fill MangaUrls table in Samrock database"},
				{"-mtti, --moveThumbsToId", "move all image to given id folder"},
				{"-rbaka, --extractRemoveBakaUrl", ""},
				{"-smc, --showMissingChapters", "show data in D:/Downloads/missingChapters.tsv"},
				{"--last-sync", "sort data in newManas.tsv updatedManas.tsv by last_sync time"},
				{"-rc, --reset-chapter[manga_id,...]", "recent chapters of given manga_id(s)"},
				{"-cmi, --change-manga-ids", "change mangaIds"},
				{"-update, --update-with-last-sync", "set samrock.mangas.last_update_time = max(mangarock.favorites.last_sync, samrock.mangas.last_update_time)"},
				{"-rrf, --remove-repeated-favorites", "remove mangas repeated in favorites"},
				{"--add-new-manga", "add new manga manually"},
				{"-urls", "edit urls"},
				{"-group", "process and move mangarock DownloadTask"}
		};
		
		System.out.println();
		String format = ANSI.yellow("%s\n    ")+"%s\n";
		for (String[] s : help) 
			System.out.printf(format, (Object[])s);
	}
	private void parse(String[] args2) {
		for (String  s : args2) {
			parse2(s);
		}
	}
	private void parse2(String key) {
		switch (key) {
			case "-h": this.help = true; break;
			case "--help": this.help = true; break;

			case "-v": this.version = true; break;
			case "--version": this.version = true; break;

			case "-abaka": this.extractAllBakaUrl = true; break;
			case "--extractAllBakaUrl": this.extractAllBakaUrl = true; break;

			case "-amf": this.analyzeMangaRockFavorites = true; break;
			case "--analyzeMangaRockFavorites": this.analyzeMangaRockFavorites = true; break;

			case "-bdtfb": this.batchDownloadThumbsFromBuid = true; break;
			case "--batchDownloadThumbsFromBuid": this.batchDownloadThumbsFromBuid = true; break;

			case "-cl": this.clean = true; break;
			case "--clean": this.clean = true; break;

			case "-cmbil": this.createMissingBuIdList = true; break;
			case "--createMissingBuIdList": this.createMissingBuIdList = true; break;

			case "-col": this.runCollective = true; break;
			case "--runCollective": this.runCollective = true; break;

			case "-cfnc": this.checkFoldersNotConverted = true; break;
			case "--check-folders-not-converted": this.checkFoldersNotConverted = true; break;

			case "-dmt": this.downloadMissingThumbs = true; break;
			case "--downloadMissingThumbs": this.downloadMissingThumbs = true; break;

			case "-dtfb": this.downloadThumbsFromBuid = true; break;
			case "--downloadThumbsFromBuid": this.downloadThumbsFromBuid = true; break;

			case "-dbt": this.downloadThumbsBoth = true; break;
			case "--downloadThumbsBoth": this.downloadThumbsBoth = true; break;

			case "-mt": this.moveThumbs = true; break;
			case "--moveThumbs": this.moveThumbs = true; break;

			case "-mtc": this.missingThumbsCheck = true; break;
			case "--missingThumbsCheck": this.missingThumbsCheck = true; break;

			case "-etrf": this.emptyThumbsResultFolder = true; break;
			case "--emptyThumbsResultFolder": this.emptyThumbsResultFolder = true; break;

			case "-eu": this.extractUrls = true; break;
			case "--extractUrls": this.extractUrls = true; break;

			case "-fbi": this.fillBuIds = true; break;
			case "--fillBuIds": this.fillBuIds = true; break;

			case "-fmr": this.finalizeMangarock = true; break;
			case "--finalizeMangarock": this.finalizeMangarock = true; break;

			case "-fu": this.fillUrls = true; break;
			case "--fillUrls": this.fillUrls = true; break;

			case "-mtti": this.moveThumbsToId = true; break;
			case "--moveThumbsToId": this.moveThumbsToId = true; break;

			case "-rbaka": this.extractRemoveBakaUrl = true; break;
			case "--extractRemoveBakaUrl": this.extractRemoveBakaUrl = true; break;

			case "-smc": this.showMissingChapters = true; break;
			case "--showMissingChapters": this.showMissingChapters = true; break;

			case "--last-sync": this.last_sync = true; break;

			case "-rc": this.resetChapter = true; break;
			case "--reset-chapter": this.resetChapter = true; break;

			case "-cmi": this.changeMangaIds = true; break;
			case "--change-manga-ids": this.changeMangaIds = true; break;

			case "-update": this.update_with_last_sync = true; break;
			case "--update-with-last-sync": this.update_with_last_sync = true; break;

			case "-rrf": this.remove_repeated_favorites = true; break;
			case "--remove-repeated-favorites": this.remove_repeated_favorites = true; break;
			case "--add-new-manga": this.add_new_manga = true; break;
			case "-urls": this.urls = true; break;
			case "-group": this.group = true; break;

			default: mangaIds.add(key); break;
		}
	}


	public static boolean lastSyncSort() {
		System.out.println(createBanner("lastSyncSort"));

		Path _new = Paths.get(NEW_MANGAS_TSV_FILE), updated = Paths.get(UPDATED_MANGAS_TSV_FILE);

		if(Files.notExists(Paths.get(MANGAROCK_INPUT_DB))){
			System.out.println(red(MANGAROCK_INPUT_DB)+"\t not found");
			return false;
		}

		Tsv newTsv,updatedTsv;

		try {
			newTsv = Files.notExists(_new) ? null : Tsv.parse(_new);
			updatedTsv = Files.notExists(updated) ? null : Tsv.parse(updated);
		} catch (IOException e) {
			System.out.println("faild file reading: "+e);
			return false;
		}       

		if(newTsv == null || updatedTsv == null)
			System.out.println(red("Files not found: ")+"\n  "+(newTsv != null ? "" : _new+"\n")+(updatedTsv != null ? "" : updated+"\n"));

		if(newTsv == null && updatedTsv == null)
			return false;

		StringBuilder sql = new StringBuilder("SELECT manga_id, last_sync FROM Favorites WHERE manga_id IN(");

		Consumer<Tsv> filler = table -> {
			if(table == null)
				return;

			Column c = newTsv.getColumn("manga_id");
			newTsv.forEach(r -> sql.append(c.get(r)).append(','));
		};

		filler.accept(newTsv);
		filler.accept(updatedTsv);

		sql.setCharAt(sql.length() -1, ')');

		System.out.println(sql);

		try(Connection c = DriverManager.getConnection(JDBC.PREFIX+MANGAROCK_INPUT_DB);
				ResultSet rs = c.createStatement().executeQuery(sql.toString());
				) {
			HashMap<String, Long> lastSync = new HashMap<>();

			while(rs.next()) lastSync.put(rs.getString("manga_id"), rs.getLong("last_sync"));

			if(newTsv != null){
				newTsv.sort((a, b) -> Objects.compare(b, a, Comparator.comparing(r -> lastSync.get(r.get("manga_id")))));
				newTsv.save(_new);
			}
			if(updatedTsv != null){
				newTsv.sort((a, b) -> Objects.compare(b, a, Comparator.comparing(r -> lastSync.get(r.get("manga_id")))));
				updatedTsv.save(updated);
			}
		} catch (SQLException|IOException e) {
			System.out.println(red("sql error in lastSyncSort()")+"  "+e);
			return false;
		}
		System.out.println(yellow("\n\nlastSyncSort -> finished"));
		return true;
	}

	static boolean cmdCheck(String input, String cmd1, String cmd2){
		return input.equalsIgnoreCase(cmd1) || input.equalsIgnoreCase(cmd2); 
	}
	/**
	 * this method will move all jpg/jpeg images in given folder to thumbFolder -> id folder 
	 */
	public static final Path JSON_CHECK_FOUND_JSONS_FILE = Paths.get("D:\\Downloads\\foundJsons.txt");
	public static final Path JSON_CHECK_NOT_FOUND_JSONS_FILE = Paths.get("D:\\Downloads\\notfoundJsons.txt");

	/**
	 * @param addAll if <b>true</b> will extract and save these  urls to bakaupdated_urls, bakaMangaNameBuidPath, otherwise will remove from bakaupdated_urls, bakaMangaNameBuidPath  
	 */
	static void extractBakaupdateUrls(boolean addAll){
		File file = filePathInputOptionPane("path to Baka-Updates Manga - Series.html", "D:\\Downloads\\Baka-Updates Manga - Series.html");

		if(file == null)
			return;

		Tsv bakaNameid = new Tsv("bu_manga_name","bu_id");
		HashSet<String> extractedUrls = new HashSet<>();

		Consumer<Element> extract = e -> {
			String url = e.absUrl("href");
			bakaNameid.addRow(e.text(), url.substring(url.lastIndexOf('=') + 1));
			extractedUrls.add(url);
		};

		try{
			Document doc = Jsoup.parse(file, "utf-8");
			doc.select(".text.pad.col1>a").stream()
			.filter(e -> !e.text().contains("(Novel)"))
			.forEach(extract);
		} catch (IOException e) {
			System.out.println(red("Failed to read : ")+file+"\n"+e);
			return;
		}

		if(bakaNameid.isEmpty()){
			System.out.println(red("nothing found - 1"));
			showHidePopup("nothind found", 2000);
			return;
		}


		/** TODO 
		 *      System.out.println(bakaNameid.getFirstRow());
        System.out.println(bakaNameid.getLastRow());
        System.out.println();
		 */


		Path root = Paths.get("D:/Core Files/Emulator/dolphin/Manga/Data/");
		Path bakaCheckedUrlsPath = root.resolve("baka_checked_urls.npp");
		Path bakaMangaNameBuidPath =  root.resolve("bakaMangaNameBuid.tsv");

		try {
			Set<String> checkedUrls = Files.exists(bakaCheckedUrlsPath) ? Files.lines(bakaCheckedUrlsPath).collect(Collectors.toSet()) : new HashSet<>();

			if(Files.exists(bakaMangaNameBuidPath)){
				Tsv exitingNameBuid = Tsv.parse(bakaMangaNameBuidPath);

				if(addAll)
					bakaNameid.merge(exitingNameBuid, true);
				else{
					Column c = bakaNameid.getColumn("bu_id");
					Set<String> set = bakaNameid.stream().map(c::get).collect(Collectors.toSet());

					Column c2 = exitingNameBuid.getColumn("bu_id");
					exitingNameBuid.removesIf(r -> set.contains(c2.get(r)));
					bakaNameid.clear();
					bakaNameid.merge(exitingNameBuid, true);
				}
			}
			if(addAll){
				extractedUrls.removeAll(checkedUrls);
				checkedUrls.addAll(extractedUrls);
			}
			else
				checkedUrls.removeAll(extractedUrls);

			System.out.println(green("\nextracted count: ")+extractedUrls.size());

			Files.write(bakaCheckedUrlsPath, checkedUrls, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			bakaNameid.save(bakaMangaNameBuidPath);
			Files.write(Paths.get("D:\\Downloads\\baka_extracted_urls.npp"), extractedUrls, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			System.out.println("failed to write : "+e);
			return;
		}

		if(extractedUrls.isEmpty()){
			System.out.println(red("nothing found - 2"));
			showHidePopup("nothind found", 2000);
			return;
		}

		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.join(System.lineSeparator(), new HashSet<>(extractedUrls))), new ClipboardOwner() {
			@Override
			public void lostOwnership(Clipboard clipboard, Transferable contents) {
			}
		});
		showHidePopup("copied to clipboard", 1500);

		System.out.println("\n\n"+FINISHED_BANNER);
	}

	static void clean(){
		if(!SESSION.contains("clean")){
			Stream.of(                  
					MANGAROCK_INPUT_DIR,
					MANGAROCK_INPUT_DB,
					MISSING_CHAPTERS_FILE,
					NEW_MANGAS_TSV_FILE,
					UPDATED_MANGAS_TSV_FILE,
					JSON_CHECK_FOUND_JSONS_FILE.toString(),
					JSON_CHECK_NOT_FOUND_JSONS_FILE.toString(),
					"D:/Downloads/Manga Index - Manga Fox!.html")
			.collect(Collectors.joining(";"));
		}

		StringBuilder inConfig = new StringBuilder(green("In Config -------------------------\n"));
		StringBuilder notFound = new StringBuilder(red("Not Found ------------------\n"));
		int inC = inConfig.length();
		int notF = notFound.length();

		JCheckBox[] obj =  StringUtils.splitStream(SESSION.getProperty("clean"),';')
				.map(String::trim)
				.map(t -> {
					Path p = Paths.get(t);
					if(Files.notExists(p)){
						notFound.append(p).append('\n');
						return null;
					}

					JCheckBox b = new JCheckBox(p.getFileName().toString());
					b.setToolTipText(t.toString());
					b.setName(t.toString());
					if(!t.equals(MANGAROCK_INPUT_DB.toString()))
						b.setSelected(true);
					return b;
				}).filter(c -> c !=null).toArray(JCheckBox[]::new);

		if(inConfig.length() != inC)
			System.out.println(inConfig.append(green("-----------------------------------")).append('\n'));

		if(notFound.length() != notF)
			System.out.println(notFound.append(red("-----------------------------------")).append('\n'));

		if(obj.length == 0){
			JOptionPane.showMessageDialog(null, "Nothing Found");
			return;
		}

		System.out.println(green("FOUND"));
		for (JCheckBox j : obj) System.out.println(j.getName());

		int option = JOptionPane.showConfirmDialog(null, obj, "Select which to delete", JOptionPane.OK_CANCEL_OPTION);

		if(option != JOptionPane.OK_OPTION){
			System.out.println(red("Cancelled"));
			return;
		}

		String deleted = green("Deleted");
		String failed = red("Failed");

		for (JCheckBox c : obj) {
			if(c.isSelected())
				System.out.println((new File(c.getName()).delete() ? deleted : failed)+"  : "+c.getName());
		}
	}

}
