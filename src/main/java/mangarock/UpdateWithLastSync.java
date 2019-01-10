package mangarock;

import static sam.manga.samrock.mangas.MangasMeta.LAST_UPDATE_TIME;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.manga.samrock.SamrockDB;
import sam.sql.querymaker.QueryMaker;
import sam.sql.sqlite.SQLiteDB;

public class UpdateWithLastSync {
    public static void main(String[] args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
        new UpdateWithLastSync();
    }
    public UpdateWithLastSync() throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
        System.out.println(ANSI.createBanner("UpdateWithLastSync"));
        
        if(Files.notExists(Paths.get(MyConfig.MANGAROCK_INPUT_DB))) {
            System.out.println("mangarock.db not found: "+MyConfig.MANGAROCK_INPUT_DB);
            return;
        }
        
        try(SQLiteDB mangarockC = new SQLiteDB(MyConfig.MANGAROCK_INPUT_DB);
                SamrockDB samrockC = new SamrockDB();
                Statement stmnt = samrockC.createStatement();
                ){
            Map<Integer, Long> mangarockMap = new HashMap<>();
            mangarockC.iterate("SELECT manga_id, last_sync FROM Favorites", rs -> mangarockMap.put(rs.getInt("manga_id"), rs.getLong("last_sync")*1000));
            
            String select = QueryMaker.getInstance().select(MANGA_ID, LAST_UPDATE_TIME).from(MANGAS_TABLE_NAME).where(w -> w.in(MANGA_ID, mangarockMap.keySet())).build();
            String update = QueryMaker.getInstance().update(MANGAS_TABLE_NAME).set(LAST_UPDATE_TIME, "%s", false).where(w -> w.eq(MANGA_ID, "%s", false)).build();
            
            samrockC.iterate(select, rs -> {
                int id = rs.getInt(MANGA_ID);
                long time = rs.getLong(LAST_UPDATE_TIME);
                stmnt.addBatch(String.format(update, compare(time, mangarockMap.get(id)), id));
            });
            
            System.out.println("executes: "+stmnt.executeBatch().length);
        }
    }
    private Object compare(Long t, Long u) {
            return u == null ?  t : Math.max(t, u);
    }
}
