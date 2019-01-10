package samrock;
import static sam.config.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.red;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.myutils.MyUtilsExtra.elvis;
import static sam.myutils.MyUtilsExtra.nullSafe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.manga.samrock.SamrockDB;
import sam.sql.querymaker.QueryMaker;
import sam.sql.sqlite.SQLiteDB;
import sam.string.StringBuilder2;
import sam.string.StringUtils;
import sam.swing.SwingUtils;
import utils.Utils;

public class ChangeMangaIds {
    private  final StringBuilder sb = new StringBuilder();
    private   final Formatter form = new Formatter(sb);
    private final List<Ids> ids;
    
    private class Ids {
        private final int samrock, mangarock;

        public Ids(int samrock, int mangarock) {
            this.samrock = samrock;
            this.mangarock = mangarock;
        }
    }

    public ChangeMangaIds() throws IOException {
        final String mangarockPath = mangarockPath(); 

        ids = getInputs();
        Files.copy(Paths.get(MyConfig.SAMROCK_DB), Utils.APP_DATA.resolve("change-ids/"+System.currentTimeMillis()+".db"));

        try(SamrockDB samrockC = new SamrockDB();
                SQLiteDB mangarockC = new SQLiteDB(mangarockPath); 
                ) {
            if(confirm(samrockC, mangarockC)) {
                execute(samrockC.createStatement());
                samrockC.commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean confirm(SamrockDB samrockC, SQLiteDB mangarockC) throws SQLException {
        String mangarockSelect = qm().select("_id", "name").from("Manga").where(w -> w.in("_id", ids, id -> id.mangarock, false)).build();

        String samrockSelect = qm().select(MANGA_ID, MANGA_NAME).from(MANGAS_TABLE_NAME).where(w -> w.in(MANGA_ID, ids, id -> id.samrock, false)).build();

        Map<Integer, String> samrockMap = new HashMap<>(); 
        samrockC.iterate(samrockSelect, rs -> samrockMap.put(rs.getInt(MANGA_ID), rs.getString(MANGA_NAME)));

        Map<Integer, String> mangarockMap = new HashMap<>(); 
        mangarockC.iterate(mangarockSelect, rs -> mangarockMap.put(rs.getInt("_id"), rs.getString("name")));

        StringBuilder2 sb = new StringBuilder2();
        sb.green("will be updated\n");
        String nul = ANSI.red("NULL");

        boolean b = false;
        for (Ids id : ids) {
            String sam = samrockMap.get(id.samrock);
            String man = mangarockMap.get(id.mangarock);

            sb.magenta("samrock   -> ").green("[ ")
            .append(id.samrock).append("  ").append(nullSafe(sam, nul))
            .green(" ] ").ln()

            .cyan("mangarock -> ").green("[ ")
            .append(id.mangarock).append("  ").append(nullSafe(man, nul))
            .green(" ] ").ln().ln();

            b = b || sam == null || man == null;
        }

        System.out.println(sb);

        if(b) {
            System.out.println(ANSI.red("\n\nHAS ERRORS, thus cancelled"));
            return false;
        }

        return JOptionPane.showConfirmDialog(null, " proceed ? ") == JOptionPane.YES_OPTION; 
    }

    private static final QueryMaker qm() {
        return QueryMaker.getInstance();
    }

    private void execute(Statement s) throws SQLException {
        String format = "UPDATE %s SET manga_id = %%d WHERE manga_id = %%d";

        String chapters = format(format, "Chapters");
        String urls = format(format, "MangaUrls");
        String manga = format(format, "Mangas");
        String recent = format(format, "Recents");
        
        for (String fm : new String[] {chapters, urls, manga, recent}) {
            for (Ids id : ids) 
                s.addBatch(format(fm, id.mangarock, id.samrock));
        }
        
        System.out.println(ANSI.green("executes: ")+s.executeBatch().length); 
    }

    private String mangarockPath() {
        String p = elvis(Files.exists(Paths.get(MANGAROCK_INPUT_DB)), MANGAROCK_INPUT_DB, MANGAROCK_DB_BACKUP);

        if(Files.notExists(Paths.get(p))) {
            System.out.println(red("mangarock.db not found"));
            System.exit(0);
        }

        System.out.println(cyan("mangarock.db: ")+p);
        return p;
    }

    private List<Ids> getInputs() {
        String str = SwingUtils.inputDialog("old_id new_id");
        if(str == null)
            System.exit(0);

        if(str.trim().isEmpty()) {
            System.out.println(red("no inputs"));
            System.exit(0);
        }

        boolean[] error = {false};
        List<Ids> inputs = Stream.of(str.split("\r?\n"))
                .map(s -> StringUtils.remove(s, '"').trim().split("\\s+"))
                .map(array -> {
                    if(array.length == 0)
                        return null;
                    if(array.length != 2) {
                        System.out.println(red("bad input: ")+Arrays.toString(array));
                        error[0] = true;
                        return null;
                    }
                    try {
                        return new Ids(Integer.parseInt(array[0]),
                                Integer.parseInt(array[1]));
                    } catch (NumberFormatException  e) {
                        System.out.println(red("bad input: ")+Arrays.toString(array)+"  "+e);
                        error[0] = true;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        if(error[0])
            System.exit(0);

        if(inputs.isEmpty()) {
            System.out.println(red("no inputs"));
            System.exit(0);
        }

        return inputs;
    }

    private String format(String format, Object...args) {
        sb.setLength(0);
        return form.format(format, args).toString();
    }
}
