package samrock;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import sam.config.MyConfig;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.mangas.MangasMeta;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.swing.SwingUtils;
import sam.tsv.Tsv;
import utils.Utils;

public class CheckMangaFolder {

	//FIXME ConvertChapter has been deleted, ScrapperConverterCommon is being used
	static class ConvertChapter {

		public ConvertChapter(int id, Double parseChapterNumber, String name, Path path, Path path2) {
			// TODO Auto-generated constructor stub
		}

		public static Tsv toTsv(List<ConvertChapter> chapters) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

    private boolean modifiedDatesModified = false;

    public void checkFoldersNotConverted() {
        Path path = Utils.APP_DATA.resolve("modifiedDates.tsv");
        
        Map<String, Long> modifiedDates =  readModifiedDates(path);
        Map<File, File[]> files = checkfoldersNotConverted(modifiedDates);

        if(modifiedDatesModified) {
            StringBuilder sb = new StringBuilder();
            modifiedDates.forEach((s,t) -> sb.append(s).append('\t').append(t).append('\n'));
            sb.setLength(sb.length() - 1);

            try {
                Files.createDirectories(path.getParent());
                Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_16), StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                Logger.getGlobal().log(Level.WARNING, "failed to save: "+path, e);
            }    
        }

        if(!files.isEmpty()) 
            saveChapters(files);
        else
            System.out.println(green("EVERYTHING-UP-TO-DATE"));
    }

    private void saveChapters(Map<File, File[]> filesMap) {
        List<ConvertChapter> chapters = new ArrayList<>();
        Map<String, File[]> map = new HashMap<>();
        filesMap.forEach((s,t) -> map.put(s.getName(), t));
        
        try(SamrockDB db = new SamrockDB()) {
            new MangaUtils(db).selectAll(rs -> {
                String dir_name = rs.getString(MangasMeta.DIR_NAME);
                File[] files = map.remove(dir_name);
                if(files == null)
                    return;
                int id = rs.getInt(MangasMeta.MANGA_ID);
                
                for (File f : files)
                    chapters.add(new ConvertChapter(id, MinimalChapter.parseChapterNumber(f.getName()).getAsDouble(), f.getName(), f.toPath(), f.toPath()));
            }, MangasMeta.MANGA_ID, MangasMeta.DIR_NAME);
            
            if(!map.isEmpty()) {
                System.out.println(red("\n\nManga id(s) not found for"));
                System.out.println("  "+String.join("\n  ", map.keySet()));
            }
            if(chapters.isEmpty())
                System.out.println(red("NOTHING TO CONVERT"));
            else {
                Tsv tsv = ConvertChapter.toTsv(chapters);
                try {
                    Path path = Paths.get("chapters.tsv");
                   if(Files.exists(path)) {
                       Path p = MyUtilsPath.findPathNotExists(path);
                       Files.move(path, p);
                       System.out.println(path+"  moved to -> "+p);
                   } 
                    tsv.save(path);
                    System.out.println(green("chapters.tsv created"));
                } catch (IOException e) {
                    System.out.println(red("failed to save: chapters.tsv")+MyUtilsException.toString(e));
                }
            }
        } catch (SQLException   e) {
            e.printStackTrace();
        }
    }

    private Map<String, Long> readModifiedDates(Path path) {
        new String(new byte[0]);
        try {
            HashMap<String, Long> map = new HashMap<>();
            Files.lines(path, StandardCharsets.UTF_16)
            .forEach(s -> {
                int i = s.indexOf('\t');
                if(i < 0)
                    return;
                map.put(s.substring(0, i), Long.parseLong(s.substring(i+1)));
            });
            return map;
        } catch (IOException e) {
            SwingUtils.showErrorDialog("failed reading: "+path, e);
        }
        return null;
    }

    /**
     * 
     * @param modifiedDates
     * @return map of dir_name -> list(folder_not_converted)
     */
    private Map<File, File[]> checkfoldersNotConverted(Map<String, Long> modifiedDates){
        Map<File, File[]> map = new HashMap<>();

        File root = new File(MyConfig.MANGA_DIR);
        File[] mangaDirs = root.listFiles(f -> f.isDirectory() && !f.isHidden() && !f.getName().equals("Data"));

        int skipped = 0;
        System.out.println(cyan("total: ")+mangaDirs.length);

        for (File file : mangaDirs) {
            Long lm = file.lastModified();
            String name = file.getName();

            if(Objects.equals(lm, modifiedDates.get(name))){
                skipped++;
                continue;
            }
            modifiedDates.put(name, lm);
            modifiedDatesModified = true;

            File[] files = file.listFiles(File::isDirectory);
            if(files.length != 0){
                map.put(file, files);
                System.out.println(yellow(name)+"  ("+files.length+")");

                for (File f : files) 
                    System.out.println("  "+f.getName());

                System.out.println();
            } else 
                System.out.println(green(name));
        }

        System.out.println(yellow("SKIPPED: ")+skipped);
        return map;
    }
}
