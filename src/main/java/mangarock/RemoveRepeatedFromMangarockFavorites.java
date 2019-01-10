package mangarock;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.sql.sqlite.SQLiteDB;

public class RemoveRepeatedFromMangarockFavorites {
    public RemoveRepeatedFromMangarockFavorites() {
        try(SQLiteDB mangarockC = new SQLiteDB(MyConfig.MANGAROCK_INPUT_DB);) {
            Stream.Builder<String[]> strm = Stream.builder();
            
            mangarockC.iterate("SELECT manga_name, manga_id, last_sync FROM Favorites", 
                    rs -> strm.accept(new String[] {
                            rs.getString("manga_name"),
                            rs.getString("manga_id"),
                            rs.getString("last_sync")
                            }));
              List<String> list = strm.build()
                    .collect(Collectors.groupingBy(s -> s[0]))
                    .entrySet()
                    .stream()
                    .map(e -> {
                        if(e.getValue().size() < 2)
                            return null;
                        e.getValue().sort(Comparator.comparing(v -> Long.parseLong(v[2])));
                        return e.getValue().get(0)[1];
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                    
              if(list.isEmpty()) {
                  System.out.println(ANSI.green("no repeated ids found"));
                  return;
              }
              System.out.println(ANSI.cyan("commits: ")+mangarockC.executeUpdate("DELETE FROM Favorites WHERE manga_id IN("+String.join(",", list)+")"));
              mangarockC.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("DONE");
    }
}
