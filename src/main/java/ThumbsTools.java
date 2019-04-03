import static sam.config.MyConfig.SAMROCK_THUMBS_DIR;
import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.swing.SwingUtils.dirPathInputOptionPane;
import static sam.swing.SwingUtils.filePathInputOptionPane;
import static sam.swing.SwingUtils.showErrorDialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

// import com.jaunt.NotFound;
//import com.jaunt.ResponseException;
//import com.jaunt.UserAgent;

import sam.config.Session;
import sam.console.ANSI;
import sam.internetutils.InternetUtils;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.mangas.MangasMeta;
import sam.manga.samrock.thumb.ThumbUtils;
import sam.manga.samrock.thumb.ThumbUtils.ExtraAndMissing;
import sam.manga.samrock.urls.MangaUrlsMeta;
import sam.manga.samrock.urls.MangaUrlsUtils;
import sam.manga.samrock.urls.MangaUrlsUtils.MangaUrl;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.tsv.Row;
import sam.tsv.Tsv;
import utils.Utils;

public class ThumbsTools {
    private static final Session SESSION = Session.getSession(ThumbsTools.class);

    final File THUMBS_DIR = new File(SAMROCK_THUMBS_DIR);
    private InternetUtils internetUtils;
    private Path tempDir ;
    private Map<String, String> selectors;

    void downloadMissingThumbs() throws IOException {
    	selectors =  new HashMap<>();
    	Files.lines(Utils.APP_DATA.resolve("ThumbsTools.selector.txt"))
    			.forEach(s -> {
    				if(s.isEmpty() || s.charAt(0) == '#')
    					return;
    				
    				int n = s.indexOf('\t');
    				if(n < 0)
    					System.out.println(ANSI.red("invalid selector line: ")+s);
    				else 
    					selectors.put(s.substring(0, n), s.substring(n+1));
    			});
        List<MissingThumb> missing = listMissingThumbs();

        if(missing == null || missing.isEmpty()){
            System.out.println("nothing to download");
            return;
        }
        tempDir  = Paths.get("downloaded_thumbs");

        try {
            Files.createDirectories(tempDir);
        } catch (IOException e1) {
            showErrorDialog("Error while creating directory: "+tempDir, e1);
            return;
        }

       // String bakaStart = "https://www.mangaupdates.com/series.html?id=";
        System.out.println("\n");
        
        internetUtils = new InternetUtils();

        for (MissingThumb m : missing) {
            int number = 1;
            System.out.println(m.manga_id+" "+ANSI.cyan(m.manga_name));
            number = download(m, MangaUrlsMeta.MANGAFOX, number);
            number = download(m, MangaUrlsMeta.MANGAHERE, number);
        }
        FileOpenerNE.openFileLocationInExplorer(tempDir.toFile());
    }
    
    
    private int download(MissingThumb m, String column, int number) {
    	System.out.println("  "+ANSI.yellow(column));
    	if(m.url == null) {
    		System.out.println(red("    m.url: null -> Failed"));
    		return number;
    	}
    	String url = m.url.getUrl(column);
        String selector = selectors.get(column);
    	
    	if(Checker.isEmptyTrimmed(url))
            System.out.println(red("    url: null -> Failed"));
        else if(Checker.isEmptyTrimmed(selector)) {
        	 System.out.println(red("    selector: null -> Failed"));
        } else {
            try {
                System.out.println("    ".concat(url));
                Document doc = Jsoup.parse(new URL(url), 60*2000);
                Elements els = doc.select(selector);

                if(els.isEmpty())
                    System.out.println(red("    count = 0"));
                else {
                    if(els.size() > 1)
                        System.out.println("    "+red("count > 1, count = "+els.size()));

                    for (Element element : els) {
                        String img = element.absUrl("src");
                        Path savePath = tempDir.resolve(m.manga_id+"_"+String.valueOf(number++)+".jpg");

                        while(Files.exists(savePath))
                            savePath = tempDir.resolve(m.manga_id+"_"+String.valueOf(number++)+".jpg");

                        try {
                            Path src = internetUtils.download(img);
                            Files.move(src, savePath, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println(green("    success: ")+savePath);
                        } catch (IOException e2) {
                            System.out.println(red("    Failed: "));
                            System.out.println("    savePath: "+savePath);
                            System.out.println("    url: "+img);
                            System.out.println("    Error: "+e2);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(red("  Failed: ")+MyUtilsException.toString(e));
            }
        }
    	return number;
	}

	public static class MissingThumb {
    	public final int manga_id;
    	public final int bu_id;
    	public final String manga_name;
    	public final MangaUrl url;

        public MissingThumb(ResultSet rs, MangaUrl url) throws SQLException {
            this.manga_id = rs.getInt(MangasMeta.MANGA_ID);
            this.bu_id = rs.getInt(MangasMeta.BU_ID);
            this.manga_name = rs.getString(MangasMeta.MANGA_NAME);
            this.url = url;
        }
    }
    List<MissingThumb> listMissingThumbs(){
        if(new File(THUMBS_DIR, "result").exists()){
            JOptionPane.showMessageDialog(null, "result folder exist, process that first");
            return null;
        }
        try (SamrockDB samrock = new SamrockDB()) {
            IntStream.Builder builder = IntStream.builder();
            MangaUtils manga = new MangaUtils(samrock);
            manga.selectAll(rs -> builder.accept(rs.getInt(MangasMeta.MANGA_ID)), MangasMeta.MANGA_ID);
            
            ExtraAndMissing em = ThumbUtils.extraAndMissingThumbs(builder.build().toArray(), THUMBS_DIR);
             
            if(em.getMissingThumbMangaIds().length == 0) {
                System.out.println(yellow("no missing thumbs"));
                return null;
            }
            else{
                List<MissingThumb> list = new ArrayList<>();
                Map<Integer, MangaUrl> map = new MangaUrlsUtils(samrock).getMangaUrls(em.getMissingThumbMangaIds()).stream().collect(Collectors.toMap(MangaUrl::getMangaId, m -> m));
                manga.select(em.getMissingThumbMangaIds(), rs -> list.add(new MissingThumb(rs, map.get(rs.getInt(MangasMeta.MANGA_ID)))), MangasMeta.MANGA_ID, MangasMeta.BU_ID, MangasMeta.MANGA_NAME);
                
                System.out.println(red("missing thumbs\t")+green("count: ")+list.size());
                System.out.println();

                String format = "%-10s%-10s%s\r\n";

                System.out.print(yellow(String.format(format, "manga_id", "bu_id", "manga_name")));
                list.forEach(m -> System.out.printf(format, m.manga_id, m.bu_id, m.manga_name));
                return list;
            }
        }
        catch (SQLException   e) {
            System.out.println("Error while check missingThumbnailCheck() : "+e);
        }
        return null;
    }

    void batchDownloadThumbsFromBuid() throws URISyntaxException{
        File file = new File("D:\\Downloads\\buId_mangaId.tsv");
        if(!file.exists())
            file = filePathInputOptionPane("path to buId_mangaId.txt", "D:\\Downloads\\buId_mangaId.tsv");

        if(file == null){
            System.out.println(red("cancelled"));
            return;
        }

        Tsv data;

        try {
            data = Tsv.parse(file.toPath());
            boolean b1 = data.containsColumn("bu_id");
            boolean b2 = data.containsColumn("manga_id");
            if(!b1 || !b2)
                throw new IllegalStateException("table has columnName? bu_id : "+b1+", columnName manga_id : "+b2);
        } catch (Exception e) {
            showErrorDialog("Error with : " + file, e);
            return;
        }

        if(data == null || data.isEmpty()){
            showErrorDialog("Nothing found in file: " + file, null);
            return;
        }

        downloadThumbsFromBuid(data);
    }
    
    void downloadThumbsFromBuid(Tsv data) throws URISyntaxException {
        if(data.isEmpty()){
            System.out.println("Nothing to download");
            return;
        }

        UIManager.put("ProgressBar.highlight", null);
        UIManager.put("ProgressBar.selectionBackground", Color.black);
        UIManager.put("ProgressBar.selectionForeground", Color.black);
        UIManager.put("ProgressBar.font", new Font(null, 1, 20));

        JProgressBar bar1 = new JProgressBar(0, data.size()+1);
        JProgressBar bar2 = new JProgressBar();

        bar1.setForeground(Color.decode("#40E0D0"));
        bar2.setForeground(Color.decode("#B0E0E6"));

        bar1.setBackground(Color.decode("#36BEB0"));
        bar2.setBackground(Color.decode("#62BBC7"));

        bar1.setStringPainted(true);
        bar2.setStringPainted(true);

        boolean[] completed = {false};
        JFrame fm = new JFrame("Donwloading");
        fm.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        fm.add(bar1);
        fm.add(bar2, BorderLayout.SOUTH);
        fm.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(completed[0])
                    System.exit(0);
                if(JOptionPane.showConfirmDialog(null, "r u sure?") == JOptionPane.YES_OPTION)
                    System.exit(0);
            }
        });
        fm.setSize(300, 130);
        fm.setVisible(true);

        //baka_id -> Set<urls>
        HashMap<String, Set<String>> existingUrls, temp;

        final Path iosphe_urlsDatPath = Utils.APP_DATA.resolve("iosphe_urls.dat");

        try {
            temp = ObjectReader.read(iosphe_urlsDatPath);
        } catch (ClassNotFoundException | IOException e2) {
            System.out.println("failed to load "+iosphe_urlsDatPath.getFileName());
            e2.printStackTrace();
            completed[0] = true;
            return;
        }

        existingUrls = temp;

        if(existingUrls == null || existingUrls.isEmpty()){
            System.out.println("something went bad, existingUrls ="+ existingUrls);
            completed[0] = true;
            return;
        }


        int[] progress = {1};
        final UserAgent agent = new UserAgent();
        final Path saveFolder =  Paths.get("downloaded_thumbs");

        try {
            Files.createDirectories(saveFolder);
        } catch (IOException e1) {
            showErrorDialog("  failed : failed to create directory: "+saveFolder, e1);
            return;
        }
        
        InternetUtils internetUtils = new InternetUtils();

        for (Row row : data) {
            bar1.setValue(progress[0]++);

            String bu_id = row.get("bu_id");
            String manga_id = row.get("manga_id");

            if(bu_id == null || bu_id.trim().isEmpty()|| !bu_id.matches("\\d+")){
                System.out.println("empty baka_id input");
                continue;
            }
            if(manga_id == null || manga_id.trim().isEmpty() || !manga_id.matches("\\d+")){
                System.out.println("empty manga_id input");
                continue;
            }

            System.out.println(yellow("downloading :")+row);

            String urlString = "http://mcd.iosphe.re/api/v1/series/".concat(bu_id).concat("/");
            bar2.setString("checking: ".concat(urlString));

            try {
                agent.visit(urlString);

                if(agent.json.size() == 1){
                    System.out.println(red("error: ")+"bu_id: "+bu_id+",  manga_id: "+manga_id+red("  error: ID not found in iosphe.re"));
                    bar2.setString("");
                    continue;
                }

                final HashSet<String> urls = new HashSet<>();

                agent.json.get("Covers").get("a").forEach(node -> {
                    /*
                     * String url = null;
                    try {
                        url = node.get("Normal").toString().replace("\\", "");
                    } catch (NotFound e) {
                        try {
                            url = node.get("Raw").toString().replace("\\", "");
                        } catch (NotFound e1) {}
                    }
                    if(url != null)
                        urls.add(url);
                     */
                });

                Set<String> set = existingUrls.get(bu_id); 

                if(set != null){
                    urls.removeAll(set);
                    set.addAll(urls);
                }
                else
                    existingUrls.put(bu_id, urls);

                bar2.setMaximum(urls.size());
                bar2.setString(null);

                int[] fileNumber = {0}, progres2 = {0};

                System.out.println(green("  downloading thumbs: "));
                urls.forEach(url -> {
                    progres2[0]++;
                    Path temp2 = saveFolder.resolve(manga_id+"_"+String.valueOf(fileNumber[0]++)+".jpg");

                    for (; Files.exists(temp2); fileNumber[0]++) temp2 = saveFolder.resolve(manga_id+"_"+String.valueOf(fileNumber[0])+".jpg");

                    try {
                        Path src = internetUtils.download(new URL(url));
                        Files.move(src, temp2, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println(green("  success: ")+ yellow("  url: ")+url+yellow("  path: ")+temp2);
                    } catch (IOException e) {
                        System.out.println(red("  failed: ")+ "  url"+url+"  path: "+temp2+"  "+e);
                    }

                    bar2.setValue(progres2[0]);
                });
            //} catch (ResponseException | NotFound e1) {
            } catch (Exception e1) {
                System.out.println(red("error: ")+"bu_id: "+bu_id+",  manga_id: "+manga_id+red("  error:"+e1));
                bar2.setString("");
                continue;
            }
        }
        try {
        	ObjectWriter.write(iosphe_urlsDatPath, existingUrls);
        } catch (NullPointerException | IOException e) {
            System.out.println("failed to write -> "+iosphe_urlsDatPath.getFileName());
            e.printStackTrace();
        }

        completed[0] = true;

        bar1.setValue(bar1.getMaximum());
        bar2.setValue(bar2.getMaximum());
        fm.toFront();
        System.out.println("\n\n"+FINISHED_BANNER);
    }


    void moveThumbsToId(){

        String temp = JOptionPane.showInputDialog("Enter id");

        if(temp == null || temp.trim().isEmpty()){
            System.out.println("empty id input");
            return;
        }

        if(!temp.trim().matches("\\d+")){
            System.out.println("input is not a number: "+temp);
            return;
        }

        String id = temp.trim();
        temp = null;

        File inputFolder = dirPathInputOptionPane("path to thumbs", "D:\\Core Files\\PrintScreen Files");

        if(inputFolder == null)
            return;

        File[] files = inputFolder.listFiles(f -> f.isFile() && f.getName().endsWith("jpg") || f.getName().endsWith("jpeg"));

        if(files.length == 0){
            System.out.println("no jpg/jpeg files found in folder");
            return;
        }

        FileOpenerNE.openFile(inputFolder);

        if(JOptionPane.showConfirmDialog(null, "Confirm to proceed?")  != JOptionPane.YES_OPTION){
            System.out.println(red("Cancelled"));
            return;
        }

        ArrayList<File> thumbs = new ArrayList<>();

        try {
            Path thumbPath = THUMBS_DIR.toPath();

            Files.walkFileTree(thumbPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if(dir.equals(thumbPath) || dir.getFileName().toString().equals(id))
                        return FileVisitResult.CONTINUE;
                    else
                        return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(file.getFileName().toString().matches(id+"(?:_\\d+)?\\.jpg"))
                        thumbs.add(file.toFile());

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.out.println("error while scanning thumb folder: "+THUMBS_DIR);
        }
        if(thumbs.isEmpty())
            System.out.println("no existing thumbs");
        else{
            System.out.println("existing thumbs");
            thumbs.forEach(f -> System.out.println("\t"+f.getParentFile().getName()+" -> "+f.getName()));
        }
        System.out.println();

        thumbs.addAll(Stream.of(files).filter(File::exists).collect(Collectors.toList()));

        String success = green("Sucess");
        String failed = red("Failed");

        System.out.println(createBanner("moving"));

        if(thumbs.size() == 1){
            File in = thumbs.get(0);
            File out = new File(THUMBS_DIR, id+".jpg");
            System.out.println("in: "+in);
            System.out.println("out: "+ out);

            System.out.println(in.renameTo(out) ? success : failed);
            System.out.println();

            FileOpenerNE.openFileLocationInExplorer(out);
        }
        else{
            Collections.sort(thumbs, Comparator.comparing(File::length).reversed());

            File idFolder = new File(THUMBS_DIR, id);

            if(!idFolder.exists() && !idFolder.mkdirs()){
                System.out.println("failed to create:  "+idFolder);
                return;
            }

            int a = 0;
            for (File in : thumbs) {
                File out = new File(idFolder, id+(a == 0 ? "" : "_"+String.valueOf(a))+".jpg_m");
                System.out.println("in: "+in);
                System.out.println("out: "+out.toString().replaceFirst("_m$", ""));
                a++;

                boolean b = in.renameTo(out);

                System.out.println(b ? success : failed);
                System.out.println();
            }

            for (File in : idFolder.listFiles()) in.renameTo(new File(in.toString().replaceFirst("_m$", "")));

            FileOpenerNE.openFile(idFolder);
        }
    }

    void moveThumbs(){
        String configKey = "moveThumbs_previous_folder";

        final File dir = dirPathInputOptionPane("Enter Path to downloaded thumbs", SESSION.getProperty(configKey));

        if(dir == null){
            System.out.println(red("cancelled"));
            return;
        }

        SESSION.put(configKey, dir.toString().replace('\\', '/'));

        File[] files = dir.listFiles(File::isFile);

        if(files.length == 0){
            System.out.println(red("Folder is empty: ")+dir);
            JOptionPane.showMessageDialog(null, "Empty folder");
            return;
        }


        if(!THUMBS_DIR.exists()){
            System.out.println(red("thumbs folder not found : ")+THUMBS_DIR);
            JOptionPane.showMessageDialog(null, "thumbs folder not found");
            return;
        }
        Path resultFolder = THUMBS_DIR.toPath().resolve("result");

        String removePattern = "(?:_\\d+)?\\.jpe?g";

        System.out.println(yellow("Listing found thumbs"));
        Map<String, List<File>> foundThumbs = Stream.of(files).collect(Collectors.groupingBy(f -> f.getName().replaceFirst(removePattern, "").trim()));

        System.out.println(yellow("Listing existing thumbs"));
        Map<String, List<File>> existingThumbs = Stream.of(THUMBS_DIR.listFiles(File::isFile)).collect(Collectors.groupingBy(f -> f.getName().replaceFirst(removePattern, "").trim()));

        foundThumbs.forEach((s,t) -> t.addAll(existingThumbs.get(s) == null ? new ArrayList<>() : existingThumbs.get(s)));

        try {
            Files.createDirectories(resultFolder);
        } catch (Exception e) {
            System.out.println(red("failed to create result folder: ")+resultFolder);           
            JOptionPane.showMessageDialog(null, "failed to create result folder");
            return;
        }

        System.out.println(green("Moving files"));
        foundThumbs.forEach((id, list) -> {
            if(list.isEmpty())
                return;

            File exitingThumbFolder;
            if((exitingThumbFolder = new File(THUMBS_DIR, id+"")).exists())
                list.addAll(Arrays.asList(exitingThumbFolder.listFiles()));

            Path temp = resultFolder.resolve(id);

            try {
                Files.createDirectories(temp);
            } catch (IOException e) {
                System.out.println(red("Failed to create directory: ")+temp);
                e.printStackTrace(System.out);
                System.out.println();
                return;
            }

            int[] a = {0};
            list.forEach(f -> {
                try {
                    Path out = temp.resolve(id+(a[0] == 0 ? "" : "_"+(a[0]++))+".jpg");

                    while(Files.exists(out))
                        out = temp.resolve(id+"_"+(a[0]++)+".jpg");

                    Files.move(f.toPath(), out, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.out.println(red("Failed to move: ")+f+"\t"+e);
                }
            });

            if(exitingThumbFolder != null)
                exitingThumbFolder.delete();
        });

        FileOpenerNE.openFileLocationInExplorer(resultFolder.toFile());
        System.out.println("delete dir: "+dir.delete());

        if(JOptionPane.showConfirmDialog(null, "Empty Result folder?") == JOptionPane.YES_OPTION)
            emptyThumbsResultFolder();
    }

    void emptyThumbsResultFolder(){
        File resultfolder = new File(THUMBS_DIR, "result");

        if(!resultfolder.exists()){
            System.out.println(resultfolder+"\t"+red("Not found"));
            return;
        }

        File[]  files = resultfolder.listFiles(File::isDirectory);

        if(files.length == 0){
            System.out.println(resultfolder+"\t"+red("does not contain any folders"));
            System.out.println("deleting result folder: "+(resultfolder.delete() ? green("success"): green("Failed")));
            return;
        }

        for (File f : files) {
            File[] f2 = f.listFiles(File::isFile);

            if(f2.length == 0){
                System.out.println(f+"\t"+red("does not contain any files"));
                System.out.println("deleting : "+f+"\t"+(f.delete() ? green("success"): green("Failed")));
            }
            else if(f2.length == 1)
                System.out.println(f2[0].getName()+"\tmoved? : "+f2[0].renameTo(new File(THUMBS_DIR, f2[0].getName()))+"\tfolder deleted: "+f.delete());
            else {
                Path out = THUMBS_DIR.toPath().resolve(f.getName());

                try {
                    if(Files.exists(out)){
                        System.out.println(red(f.getName()+"-> folder move failed\t")+out+red("\t already exists"));
                        continue;
                    }
                    Files.move(f.toPath(), out, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(green("sucess: ")+f.getName()+"\tcount : "+f2.length);
                } catch (IOException e) {
                    System.out.println(red(f.getName()+"-> folder move failed\t")+out+"\t"+e);
                }
            }
        }

        System.out.println();
        System.out.println();
        System.out.println("deleting result folder: "+(resultfolder.delete() ? green("success"): green("Failed")));
    }


}
