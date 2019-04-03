import static sam.console.ANSI.red;
import static sam.manga.samrock.mangas.MangasMeta.BU_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.swing.SwingPopupShop.showHidePopup;
import static sam.swing.SwingUtils.filePathInputOptionPane;
import static sam.swing.SwingUtils.showErrorDialog;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import sam.io.fileutils.FileOpener;
import sam.manga.samrock.SamrockDB;
import sam.sql.querymaker.QueryMaker;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;

public class BuIdTools {
    
    private class BUID {
        private final int manga_id, bu_id;

        public BUID(int manga_id, int bu_id) {
            this.manga_id = manga_id;
            this.bu_id = bu_id;
        }
    }

    void createMissingBuIdList(){
        Path bakaMangaNameBuidPath = Paths.get("D:/Core Files/Emulator/dolphin/Manga/Data/bakaMangaNameBuid.tsv");

        /*
         * bu_name(lower_cased) -> Row(columnName {bu_name, bu_id})
         * 
         * bu_name is same as manga_name, bu_name is obtained from bakaupdtates
         * manga_name may or may not have manhwa or manhua, bu_name does'nt 
         */
        Tsv buNameIdTable = null; 

        try {
            buNameIdTable = Tsv.parse(bakaMangaNameBuidPath);
            Column columnNumber = buNameIdTable.getColumn("bu_manga_name"); 
            buNameIdTable.forEach(row -> columnNumber.set(row, columnNumber.get(row).toLowerCase()));
        } catch (IOException e) {
            System.out.println(red("Failed to parse tsvFile: ")+bakaMangaNameBuidPath+"\t"+e);
        }

        Tsv tsv = new Tsv(MANGA_ID, MANGA_NAME, BU_ID);
        
        try (SamrockDB samrock = new SamrockDB()) {
            samrock.iterate(QueryMaker.getInstance().select(MANGA_ID, MANGA_NAME, BU_ID).from(MANGAS_TABLE_NAME).where(w -> w.eq(BU_ID, -1)).build(),
                    rs -> tsv.addRow(rs.getString(MANGA_ID), rs.getString(MANGA_NAME), rs.getString(BU_ID)));

            try {
                tsv.save(Paths.get("D:/Downloads/missing_buId.tsv"));
                showHidePopup("missing_buId.tsv created", 1500);
                Desktop.getDesktop().browse(new URI("https://www.mangaupdates.com/search.html"));
                FileOpener.openFile(new File("D:/Downloads/missing_buId.tsv"));
            } catch (IOException | URISyntaxException e) {
                showErrorDialog("Failed to write file", e);
            }
        }

        catch (SQLException   e) {
            showErrorDialog("Sql Error", e);
        }
    }

    void fillBuIds(){
        File file = new File("D:\\Downloads\\missing_buId.tsv");
        file = file.exists() ? file : filePathInputOptionPane("path to missing_buId.tsv", "D:\\Downloads\\missing_buId.tsv"); 

        if(file == null){
            System.out.println(red("Cancelled"));
            return;
        }

        //[i] -> {manga_id, bu_id};
        //in case of bad data manga_id = -1
        BUID[] buids;

        try {
            Tsv data = Tsv.parse(file.toPath());

            boolean b1, b2;
            if(b1 = !data.containsColumn(BU_ID))
                System.out.println("tsv does'nt have column with name: "+BU_ID);
            if(b2 = !data.containsColumn(MANGA_ID))
                System.out.println("tsv does'nt have column with name: "+MANGA_ID);

            if(b1 || b2){
                System.out.println(red("Operation cancelled automatically, , no filling processed"));
                return; 
            }
            if(data.isEmpty()){
                System.out.println(red("empty file, no filling processed"));
                return;
            }
            Column mangaId = data.getColumn(MANGA_ID);
            Column buid = data.getColumn(BU_ID);

            buids = new BUID[data.size()];

            int i = -1, errorCount = 0;
            for (Row row : data) {
                i++;
                try {
                    buids[i] = new BUID(mangaId.getInt(row), buid.getInt(row));
                } catch (NumberFormatException|NullPointerException e) {
                    System.out.println("bad row: "+row+"\tError: "+e);
                    errorCount++;
                }
            }
            if(data.size() == errorCount){
                System.out.println(red("All Row are bad, no filling processed"));
                return;
            }
        } catch (IOException e) {
            System.out.println(red("unable to parse tsv: ")+file); 
            e.printStackTrace();
            return;
        }
        try(SamrockDB samrock = new SamrockDB();) {
            samrock.prepareStatementBlock(QueryMaker.getInstance().update(MANGAS_TABLE_NAME).placeholders(BU_ID).where(w -> w.eqPlaceholder(MANGA_ID)).build(), ps -> {
                for (BUID buid : buids) {
                    if(buid == null)
                        continue;
                    ps.setInt(1, buid.bu_id);
                    ps.setInt(1, buid.manga_id);
                    ps.addBatch();
                }
                System.out.println("inserts: "+ps.executeBatch().length);
                return null;
            });
            samrock.commit();
        }
        catch (SQLException   e) {
            showErrorDialog("Sql Error", e);
        }


    }


}
