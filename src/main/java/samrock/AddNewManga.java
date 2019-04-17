package samrock;

import static sam.fx.helpers.FxButton.button;
import static sam.fx.helpers.FxGridPane.gridPane;
import static sam.manga.mangarock.MangarockMangaMeta.AUTHOR;
import static sam.manga.mangarock.MangarockMangaMeta.CATEGORIES;
import static sam.manga.mangarock.MangarockMangaMeta.DESCRIPTION;
import static sam.manga.mangarock.MangarockMangaMeta.ID;
import static sam.manga.mangarock.MangarockMangaMeta.MANGA_TABLE_NAME;
import static sam.manga.mangarock.MangarockMangaMeta.NAME;
import static sam.manga.mangarock.MangarockMangaMeta.RANK;
import static sam.manga.mangarock.MangarockMangaMeta.STATUS;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.Callable;

import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxBindings;
import sam.fx.helpers.FxConstants;
import sam.fx.helpers.FxGridPane;
import sam.fx.helpers.FxHBox;
import sam.fx.popup.FxPopupShop;
import sam.manga.mangarock.MangarockDB;
import sam.manga.samrock.Renamer;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.mangas.MangasMeta;
import sam.manga.samrock.urls.MangaUrlsMeta;
import sam.manga.samrock.urls.nnew.MangaUrlsUtils;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.sql.JDBCHelper;

public class AddNewManga extends Application {
	private final TextField id = tf();
	private final TextField manga_name = tf();
	private final TextField dirname = tf();
	private final TextField author = tf();
	private final TextField categories = tf();
	private final TextField url = tf();
	private final TextField mangakakalot = tf();
	private final TextField rank = tf();
	private final Text status = new Text();
	private final TextArea description = new TextArea();
	private final SimpleBooleanProperty not_saveable = new SimpleBooleanProperty(true);

	private MangarockDB mangarock;
	private SamrockDB samrock;
	private final BorderPane root = new BorderPane();

	@Override
	public void start(Stage stage) throws Exception {
		GridPane grid = gridPane(5);
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);

		description.setEditable(false);
		id.setEditable(true);
		id.setOnAction(this::load);
		url.setEditable(true);

		int row = 0;
		Button btn = button("load", this::load, loadDisable());
		grid.addRow(row++, text("id"), id, btn);
		grid.addRow(row++, text("manga_name"), manga_name);
		grid.addRow(row++, text("dir_name"), dirname);
		grid.addRow(row++, text("author"), author);
		grid.addRow(row++, text("categories"), categories);
		grid.addRow(row++, text("rank"), rank);
		grid.addRow(row++, text("status"), status);
		grid.addRow(row++, text("mangafox"), url);
		grid.addRow(row++, text("mangakakalot"), mangakakalot);
		grid.addRow(row++, text("description"));
		grid.add(description, 0, row++, GridPane.REMAINING, GridPane.REMAINING);

		GridPane.setColumnSpan(id, 2);
		GridPane.setColumnIndex(btn, 3);
		GridPane.setColumnSpan(rank, 2);

		for (Node n : new Node[]{manga_name, dirname, author, categories}) {
			GridPane.setColumnSpan(n, GridPane.REMAINING);	
		}

		ColumnConstraints c = new ColumnConstraints();
		c.setFillWidth(true);
		c.setHgrow(Priority.ALWAYS);
		c.setMaxWidth(Double.MAX_VALUE);

		FxGridPane.setColumnConstraint(grid, 4, c);

		RowConstraints r = new RowConstraints();
		r.setFillHeight(true);
		r.setVgrow(Priority.ALWAYS);
		r.setMaxHeight(Double.MAX_VALUE);

		FxGridPane.setRowConstraint(grid, row, r);

		grid.setStyle("-fx-font-family:monospace;-fx-padding:5px");
		root.setCenter(grid);
		root.setBottom(FxHBox.buttonBox(button("save", this::save, not_saveable)));

		stage.setScene(new Scene(root));
		stage.setTitle("Add new Manga");
		stage.show();
	}

	private ObservableValue<? extends Boolean> loadDisable() {
		return FxBindings.map(id.textProperty(), s -> Checker.isEmptyTrimmed(s) || s.chars().anyMatch(d -> d < '0' || d > '9'));
	}
	@Override
	public void stop() throws Exception {
		try {
			if(mangarock != null)
				mangarock.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			if(samrock != null)
				samrock.close();	
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private String insert_sql;

	private void save(ActionEvent e) {
		if(samrock == null) {
			samrock = systemError(SamrockDB::new);
			insert_sql = JDBCHelper.insertSQL(MangasMeta.MANGAS_TABLE_NAME,
					MangasMeta.MANGA_ID,
					MangasMeta.DIR_NAME,
					MangasMeta.MANGA_NAME, 
					MangasMeta.AUTHOR,
					MangasMeta.DESCRIPTION,
					MangasMeta.CATEGORIES,
					MangasMeta.STATUS,
					MangasMeta.RANK,
					MangasMeta.LAST_READ_TIME,
					MangasMeta.LAST_UPDATE_TIME
					);
		}
			
		if(samrock == null)
			return;
		
		systemError(() -> {
			try(PreparedStatement ps = samrock.prepareStatement(insert_sql)) {
				int c = 1;
				ps.setInt(c++, Integer.parseInt(current_id));
				ps.setString(c++, dirname.getText());
				ps.setString(c++, manga_name .getText());
				ps.setString(c++, author.getText());
				ps.setString(c++, description.getText());
				ps.setString(c++, categories.getText());
				ps.setBoolean(c++, (boolean)status.getUserData());
				ps.setInt(c++, Integer.parseInt(rank.getText()));
				ps.setLong(c++, System.currentTimeMillis());
				ps.setLong(c++, System.currentTimeMillis());
				
				ps.execute();
				
				String mangafox = tranform(this.url.getText());
				String mangakakalot = tranform(this.mangakakalot.getText());
				
				if(mangafox != null || mangakakalot != null ) {
					try(PreparedStatement p = samrock.prepareStatement(JDBCHelper.insertSQL(MangaUrlsMeta.TABLE_NAME, MangaUrlsMeta.MANGA_ID, MangaUrlsMeta.MANGAHERE, MangaUrlsMeta.MANGAKAKALOT))) {
						p.setString(1, current_id);
						set(2, mangafox == null ? null : MangaUrlsUtils.name(mangafox), p);
						set(3, mangakakalot, p);
						p.execute();
					}
				}
				
				samrock.commit();
			}
			FxPopupShop.showHidePopup("saved: "+current_id, 2500);
			return null;
		});
		not_saveable.set(true);
	}

	private String tranform(String s) {
		return Checker.isEmptyTrimmed(s) ? null : s.trim();
	}

	private void set(int index, String value, PreparedStatement p) throws SQLException {
		if(value != null)
			p.setString(index, value);
		else
			p.setNull(index, Types.VARCHAR);
	}

	private StringBuilder select_sql;
	private int select_sql_n;
	private String current_id; 
	
	private void load(ActionEvent e) {
		current_id = this.id.getText();

		if(mangarock == null){
			mangarock = systemError(MangarockDB::new);
			select_sql = JDBCHelper.selectSQL(MANGA_TABLE_NAME, AUTHOR, CATEGORIES, NAME, RANK, ID, STATUS, DESCRIPTION)
					.append(" WHERE _id = ");
			
			select_sql_n = select_sql.length() - 1;
		}

		if(mangarock == null)
			return;
		
		select_sql.setLength(select_sql_n);
		select_sql.append(current_id).append(';');
		
		systemError(() -> {
			try(ResultSet rs = mangarock.executeQuery(select_sql.toString())) {
				if(!rs.next())
					FxAlert.showErrorDialog(null, "no manga found for: "+current_id, null);
				else {
					String s = rs.getString(NAME);
					manga_name.setText(s);
					dirname.setText(Renamer.mangaDirName(s));
					author.setText(rs.getString(AUTHOR));
					categories.setText(rs.getString(CATEGORIES));
					rank.setText(rs.getString(RANK));
					description.setText(rs.getString(DESCRIPTION));
					boolean b = rs.getBoolean(STATUS);
					status.setText(b ? "Completed" : "OnGoing");
					status.setUserData(b);
					
					url.setText(null);
					not_saveable.set(false);
				}
			}
			return null;
		});
	}

	private <E> E systemError(Callable<E> callable) {
		try {
			return callable.call();
		} catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			MyUtilsException.append(sb, e, true);
			root.getChildren().clear();
			TextArea t = new TextArea(sb.toString());
			BorderPane.setMargin(t, FxConstants.INSETS_5);
			root.setCenter(t);
		}
		return null;
	}

	private Text text(String s) {
		Text t = new Text(s);
		t.setTextAlignment(TextAlignment.RIGHT);
		return t;
	}
	private TextField tf() {
		TextField t = new TextField();
		t.setEditable(false);
		return t;
	}


}
