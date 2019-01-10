package samrock;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.mangas.MangasMeta;
import sam.sql.querymaker.QueryMaker;

public class ResetChapters {
      /**
     * list mangas and chapters not in samrock but in mangarock 
     * @throws IOException 
     * @throws URISyntaxException 
     */
    public ResetChapters(List<String> mangaIds) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
    	if(10 < System.currentTimeMillis())
    		throw new IllegalAccessError("not yet implmented");
        if(mangaIds == null || mangaIds.isEmpty()) {
            System.out.println(ANSI.red("no manga_id(s) specified"));
            return;
        }

        Map<Boolean, LinkedHashSet<String>> map = mangaIds.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.partitioningBy(s -> s.chars().allMatch(Character::isDigit), Collectors.toCollection(LinkedHashSet::new)));

        Optional.ofNullable(map.get(false))
        .filter(l -> !l.isEmpty())
        .ifPresent(list -> System.out.println(ANSI.red("bad inputs: ")+list));

        Set<String> list = map.get(true);
        if(list == null){
            System.out.println(ANSI.red("no manga_id(s) specified"));
            return;
        }

        List<Integer> ids = list.stream().map(Integer::parseInt).collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        try(SamrockDB db = new SamrockDB()) {
            Map<Integer, String> idDirnameMap = new HashMap<>();
            new MangaUtils(db).select(ids, rs -> idDirnameMap.put(rs.getInt(MangasMeta.MANGA_ID), rs.getString(MangasMeta.DIR_NAME)), MangasMeta.MANGA_ID, MangasMeta.DIR_NAME);

            StringBuilder sb = new StringBuilder();
            /** FIXME
            List<ChapterWithMangaId> temp = new ArrayList<>();
            new ChapterUtils(db) .updateChaptersInDB(temp, ids, sb);  
             */
            

            db.prepareStatementBlock(QueryMaker.getInstance()
                    .update(MangasMeta.MANGAS_TABLE_NAME)
                    .placeholders(MangasMeta.LAST_UPDATE_TIME)
                    .where(w -> w.eqPlaceholder(MangasMeta.MANGA_ID))
                    .build()
                    , ps -> {
                        for (Integer id : ids) {
                            String dirname = idDirnameMap.get(id);

                            if(dirname == null) {
                                System.out.println(ANSI.red("no manga associated with manga_id: ")+id);
                                continue;
                            }

                            ps.setLong(1, new File(MyConfig.MANGA_DIR, dirname).lastModified());
                            ps.setInt(2, id);
                            ps.addBatch();
                        }
                        return ps.executeBatch().length;
                    });

            System.out.println(sb);
            db.commit();
        }
    }
}
