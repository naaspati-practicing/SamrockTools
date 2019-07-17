package mangarock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;

import sam.config.MyConfig;
import sam.console.ANSI;
import sam.io.fileutils.FileNameSanitizer;
import sam.manga.mangarock.MangarockDB;
import sam.manga.samrock.Renamer;

public class Grouping implements Callable<Void> {
	
	private static class DownloadTask {
		private final String chapter_name, dir_name;
		private final int manga_id;
		
		public DownloadTask(String chapter_name, String dir_name, int manga_id) {
			this.chapter_name = chapter_name;
			this.dir_name = dir_name;
			this.manga_id = manga_id;
		}
	}

	@Override
	public Void call() throws Exception {
		JFileChooser jfc = new JFileChooser(new File(MyConfig.COMMONS_DIR));
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		
		jfc.showOpenDialog(null);
		File file = jfc.getSelectedFile();
		System.out.println(file);
		
		if(file == null || !file.exists()) {
			System.out.println(ANSI.red("cencelled"));
			return null;
		}
		
		Path src = file.toPath();
		
		List<Path> dirs = Files.list(src)
		.filter(Files::isDirectory)
		.collect(Collectors.toList());
		
		if(dirs.isEmpty()) {
			System.out.println(ANSI.red("no subdirs found in: ")+file);
			return null;
		}
		
		try(MangarockDB db = new MangarockDB()) {
			List<DownloadTask> downloadTasks = db.collectToList("select chapter_name, dir_name, manga_id from DownloadTask", rs -> new DownloadTask(rs.getString(1), rs.getString(2), rs.getInt(3)));
			
			if(downloadTasks.isEmpty()) {
				System.out.println("no DownloadTask found");
				return null;
			}
			
			int[] ids = downloadTasks.stream().mapToInt(t -> t.manga_id).distinct().sorted().toArray();
			StringBuilder sb = new StringBuilder("select _id, name from Manga where _id in(");
			
			for (int i : ids) 
				sb.append(i).append(',');
			sb.setCharAt(sb.length() - 1, ')');
			sb.append(';');
			
			Path mdir = Paths.get(MyConfig.MANGA_DIR);
			System.out.println("manga_dir: "+mdir);
			
			IntUnaryOperator index = id -> Arrays.binarySearch(ids, id);
			
			Path[] mangaDirs = new Path[ids.length];
			db.iterate(sb.toString(), s -> {
				mangaDirs[index.applyAsInt(s.getInt(1))] = mdir.resolve(Renamer.mangaDirName(s.getString(2))); 
			});
			
			for (Path p : mangaDirs)
				Files.createDirectories(p);
			
			int len = mdir.getNameCount();
			int total = 0, moved = 0;
			int[] count = new int[ids.length];
			Pattern pattern = Pattern.compile("^Chapter ", Pattern.CASE_INSENSITIVE);
			
			for (DownloadTask d : downloadTasks) {
				int n = d.dir_name.lastIndexOf('/');
				if(n < 0) {
					System.out.println("bad dirname: "+d.dir_name);
					continue;
				}
				
				String dirname = d.dir_name.substring(n+1);
				Path p = src.resolve(dirname);
				
				total++;
				
				if(Files.notExists(p))
					System.out.println(ANSI.red("not found: ")+dirname);
				else {
					int ndx = index.applyAsInt(d.manga_id);
					String chapName = pattern.matcher(d.chapter_name).replaceFirst("").trim();
					Path t = mangaDirs[ndx].resolve(FileNameSanitizer.sanitize(chapName));
					Files.move(p, t, StandardCopyOption.REPLACE_EXISTING);
					System.out.println(ANSI.yellow("moved: ")+dirname+ANSI.cyan(" -> ")+t.subpath(len, t.getNameCount()));
					count[ndx]++;
					moved++;
				}
			}
			
			System.out.println(ANSI.yellow("\n\nmoved: ")+moved+"/"+total);
			
			for (int i = 0; i < count.length; i++) 
				System.out.println(ids[i] + ": "+ mangaDirs[i].getFileName() +ANSI.cyan(" -> ")+count[i]);
		}
		
		return null;
	}

}
