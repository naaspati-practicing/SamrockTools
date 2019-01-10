package utils;
import static sam.config.MyConfig.MANGAROCK_DB_BACKUP;
import static sam.config.MyConfig.MANGAROCK_INPUT_DB;
import static sam.myutils.MyUtilsExtra.elvis;
import static sam.myutils.MyUtilsExtra.nullSafe;
import static sam.myutils.System2.lookupAny;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import sam.console.ANSI;
public final class Utils {
    private Utils() {}
    
    public static final Path APP_DATA;
    
    static {
        Path p = Paths.get(nullSafe(lookupAny("APP_DATA_DIR", "app_data", "APP_DATA","APP.DATA.DIR", "app.data", "APP.DATA", "SELF_DIR", "self_dir"), "app_data"));
        
        if(Files.notExists(p) || !Files.isDirectory(p)) {
            System.out.println(ANSI.red("folder not found: ")+p);
            System.exit(0);
        }
        APP_DATA = p;
    }

	public static String mangarock() {
		return elvis(Files.exists(Paths.get(MANGAROCK_INPUT_DB)), MANGAROCK_INPUT_DB, MANGAROCK_DB_BACKUP);
	}

	public static void init() {}

}
