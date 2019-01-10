package mangarock;

import static sam.console.ANSI.red;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import extras.Manga;
import sam.config.Session;
import sam.manga.samrock.Renamer;
import sam.myutils.System2;
import sam.string.StringBuilder2;

public class MangaCheck {
	private static final Session SESSION = Session.getSession(MangaCheck.class);
	
	private final List<Manga> nnew, updated, samrockData;
	public final boolean updatesError, newError;
	private final boolean caseSensetive;

	public MangaCheck(List<Manga> nnew, List<Manga> updated, List<Manga> samrockData) {
		this.nnew = nnew;
		this.updated = updated;
		this.samrockData = samrockData;
		
		SESSION.putIfAbsent("case_sensetive", "false");
		caseSensetive = Boolean.valueOf(SESSION.getProperty("case_sensetive"));
		
		this.updatesError = System2.lookupBoolean("SKIP_UPDATE_CHECK", false) ? false : updatedErrors();
		this.newError = System2.lookupBoolean("SKIP_NEW_CHECK", false) ? false : newErrors();
	}
	
    private boolean updatedErrors() {
        boolean updatesError = false;
        
        if(!updated.isEmpty()) {
            StringBuilder2 sb = new StringBuilder2();
            sb.red("Manga Updates Conflicts \n-------------------------\n");
            Map<Integer, Manga> samrockMap = samrockData.stream().collect(Collectors.toMap(m -> m.id, m -> m)) ;

            for (Manga idName : updated) {
                Manga manga = samrockMap.get(idName.id);
                String dirName = Renamer.mangaDirName(idName.name);

                boolean name = equals(idName.name, manga.name);
                boolean dir = equals(dirName, manga.dir_name);

                if(name && dir)
                    continue;

                name = !name;
                dir = !dir;

                updatesError = true;

                sb
                .append("manga_id: ")
                .append(idName.id)
                .red(", type: ")
                .append((name && dir ? "manga_name and dir_name" : name ? "manga_name" : "dir_name"))
                .append("\n");
                if(name)
                	append(sb, "manga_name", manga.name, idName.name);
                if(dir)
                	append(sb, "dir_name", manga.dir_name, idName.dir_name);
                sb.ln();
            }

            if(updatesError)
                System.out.println(sb.append(red("\n-------------------------\n")));
        }
        return updatesError;
    }
    
    private boolean equals(String s1, String s2) {
    	if(caseSensetive)
    		return Objects.equals(s1, s2);
		return s1 != null && s1.equalsIgnoreCase(s2);
	}

	private final StringBuilder sb2 = new StringBuilder();
    
    private void append(StringBuilder2 sb, String title, String samrock, String mangarock) {
    	sb.append("               ").yellow(title).ln();
    	 sb.append("  samrock   -> ");
    	sb2.setLength(0);
    	sb2.append("  mangarock -> ");
    	
    	int size = Math.min(samrock.length(), mangarock.length());
    	for (int i = 0; i < size; i++) {
			char s = samrock.charAt(i);
			char m = mangarock.charAt(i);
			
			if(s == m || (!caseSensetive && uc(s) == uc(m))) {
				sb.append(s);
				sb2.append(m);
			} else {
				sb.red(sc(s));
				red(sb2, sc(m));
			}
		}
    	if(samrock.length() > size)
    		sb.red(samrock.substring(size));
    	if(mangarock.length() > size)
    		red(sb2, mangarock.substring(size));
    	sb.ln();
    	sb.append(sb2).ln();
	}

	private String sc(char c) {
		return String.valueOf(c);
	}

	private char uc(char c) {
		return Character.toUpperCase(c);
	}

	private boolean newErrors() {
        boolean error = false;

        if(!nnew.isEmpty()) {
            Map<String, Manga> dirNames0 = new HashMap<>(); 
            Map<String, Manga> mangaNames0 = new HashMap<>();

            samrockData.forEach(m -> {
                dirNames0.put(m.dir_name.toLowerCase(), m);
                mangaNames0.put(m.name.toLowerCase(), m);
            });

            IdentityHashMap<Manga, Set<Manga>> conflicts = new IdentityHashMap<>();
            Function<Manga, Set<Manga>> computer = d -> new HashSet<>();

            for (Manga idName : nnew) {
                String dirName = Renamer.mangaDirName(idName.name);

                Manga byName = mangaNames0.get(idName.name.toLowerCase());
                Manga byDirName = dirNames0.get(dirName.toLowerCase());

                if(byName == null && byDirName == null)
                    continue;

                error = true;

                if(byName != null) conflicts.computeIfAbsent(byName, computer).add(idName);
                if(byDirName != null) conflicts.computeIfAbsent(byDirName, computer).add(idName);
            }

            if(error) {
                StringBuilder2 sb = new StringBuilder2();
                sb.red("Manga New Conflicts \n------------------------------\n");
                sb.yellow("from -> [ id, name, dirname ]").ln();
                StringBuilder sb2 = new StringBuilder();

                conflicts.forEach((manga,idname) -> {
                    sb.magenta("samrock   -> ").green("[ ")
                    .append(manga.id).append(", ")
                    .append(manga.name).append(", ")
                    .append(manga.dir_name).green(" ] ").ln();

                    idname.forEach(in -> {
                        sb.cyan("mangarock -> ").green("[ ")
                        .append(in.id).append(", ")
                        .append(in.name).append(", ")
                        .append(Renamer.mangaDirName(in.name)).green(" ] ").ln();

                        sb2.append(manga.id).append(" -> ").append(in.id).append('\n');
                    });
                    sb.ln();
                });
                System.out.println(sb.append("\nid changes\n").append(sb2));
            }
        }
        return error;
    }
	
}
