package urls;

import static  sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static  sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static  sam.console.ANSI.createBanner;
import static  sam.console.ANSI.red;
import static  sam.manga.samrock.urls.MangaUrlsMeta.MANGAFOX;
import static  sam.manga.samrock.urls.MangaUrlsMeta.MANGAHERE;
import static  sam.manga.samrock.urls.MangaUrlsMeta.MANGA_ID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import sam.console.ANSI;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.urls.MangaUrlsUtils;
import sam.manga.samrock.urls.MangaUrlsUtils.MangaUrl;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;

public class FillUrls {
    boolean returnFalse;

    public boolean fillUrls(){
        System.out.println("\n"+createBanner("Filling urls")+"\n");

        if(Files.notExists(Paths.get(NEW_MANGAS_TSV_FILE)) && Files.notExists(Paths.get(UPDATED_MANGAS_TSV_FILE))){
            System.out.println(NEW_MANGAS_TSV_FILE+red("\tdoes'nt exists\n")+
                    UPDATED_MANGAS_TSV_FILE+red("\tdoes'nt exists\nNothng filled"));
            return false;
        }

        Tsv newMangasTsv = null, updatedMangasTsv = null;

        if(Files.exists(Paths.get(NEW_MANGAS_TSV_FILE))){
            try {
                newMangasTsv = Tsv.parse(Paths.get(NEW_MANGAS_TSV_FILE));
            } catch (IOException e) {
                System.out.println("failed to parse: "+NEW_MANGAS_TSV_FILE+"\tError: "+e);
            }
        }
        else
            System.out.println(NEW_MANGAS_TSV_FILE+red("\tdoes'nt exists"));

        if(Files.exists(Paths.get(UPDATED_MANGAS_TSV_FILE))){
            try {
                updatedMangasTsv = Tsv.parse(Paths.get(UPDATED_MANGAS_TSV_FILE));
            } catch (IOException e) {
                System.out.println(red("failed to parse: ")+UPDATED_MANGAS_TSV_FILE+"\tError: "+e);
            }
        }
        else
            System.out.println(UPDATED_MANGAS_TSV_FILE+red("\tdoes'nt exists"));

        if((updatedMangasTsv == null || updatedMangasTsv.isEmpty()) && (newMangasTsv == null || newMangasTsv.isEmpty())){
            System.out.println(red("nothing loaded"));
            return false;
        }

        return fillUrls(newMangasTsv, updatedMangasTsv);
    }
    public boolean fillUrls(Tsv newMangasTsv, Tsv updatedMangasTsv){
        returnFalse = false;

        if((updatedMangasTsv == null || updatedMangasTsv.isEmpty()) && (newMangasTsv == null || newMangasTsv.isEmpty())){
            System.out.println(red("nothing data found to fill"));
            return false;
        }

        try(SamrockDB db = new SamrockDB()) {
            List<MangaUrl> urls = new ArrayList<>();
            fillUrls(updatedMangasTsv, urls,db);
            fillUrls(newMangasTsv, urls,db);
            
           // save(updatedMangasTsv, UPDATED_MANGAS_TSV_FILE);
           // save(newMangasTsv, NEW_MANGAS_TSV_FILE);

            urls.removeIf(Objects::isNull);

            if(!urls.isEmpty()) {
                System.out.println(ANSI.yellow("commits:")+new MangaUrlsUtils(db).commitMangaUrls(urls));
                db.commit();
            }
        } catch (SQLException   e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
   
    private  void fillUrls(Tsv tsv, List<MangaUrl> urls,SamrockDB db) throws SQLException {
        if(tsv == null || tsv.isEmpty())
            return;

		Column manga_id = tsv.getColumn(MANGA_ID);
		Column fox = tsv.getColumn(MANGAFOX);
		Column here = tsv.getColumn(MANGAHERE);

        Iterator<Row> it = tsv.iterator();
        while (it.hasNext()) {
            Row row = it.next();

            String f = fox.get(row);
            String h = here.get(row);

            MangaUrl u = new MangaUrlsUtils(db).parseMangaUrl(manga_id.getInt(row), f, h);

            if(u == null)
                System.out.println(red("bad data: ")+row);
            else {
                urls.add(u);
                it.remove();

                if(u.getMangafoxName() == null && f != null)
                    System.out.println(red("bad mangafox: ")+row);
                if(u.getMangahereName() == null && h != null)
                    System.out.println(red("bad mangahere: ")+row);
            }

        }

    }

}
